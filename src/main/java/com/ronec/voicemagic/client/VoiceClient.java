package com.ronec.voicemagic.client;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractCaster;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.mojang.blaze3d.platform.InputConstants;
import com.ronec.voicemagic.ArsVoiceSupport;
import com.ronec.voicemagic.VoiceMagicMod;
import com.ronec.voicemagic.audio.MicCapture;
import com.ronec.voicemagic.network.ArsVoiceCastPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Ars Nouveau voice casting with continuous phrase detection and an unobtrusive audio-reactive ring. */
@EventBusSubscriber(modid=VoiceMagicMod.MOD_ID,value=Dist.CLIENT)
public final class VoiceClient {
    private static final KeyMapping CAST=new KeyMapping("key.voicemagic.cast",KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_ALT,"key.categories.voicemagic");
    private static final KeyMapping BOOK=new KeyMapping("key.voicemagic.book",InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,"key.categories.voicemagic");
    private static final KeyMapping MODE=new KeyMapping("key.voicemagic.mode",KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_SEMICOLON,"key.categories.voicemagic");
    private static final KeyMapping UNDO=new KeyMapping("key.voicemagic.undo",KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_BACKSPACE,"key.categories.voicemagic");

    private static UUID loadedId;
    private static VoiceProfile profile;
    private static MicCapture mic;
    private static boolean capturing,finishing,cancel,wasCastingKeyDown,liveMode;
    private static InteractionHand heldHand;
    private static final List<String> spoken=new ArrayList<>();
    private static volatile List<String> preview=List.of();
    private static final AtomicBoolean previewBusy=new AtomicBoolean();
    private static long captureSession;
    private static double smoothLevel;
    private VoiceClient(){}

    @EventBusSubscriber(modid=VoiceMagicMod.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class ModKeys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e){
            e.register(CAST);e.register(BOOK);e.register(MODE);e.register(UNDO);
        }
    }
    public static boolean isLiveMode(){return liveMode;}
    public static void toggleMode(){
        if(capturing||finishing)return;
        liveMode=!liveMode;
        announce(liveMode?"Improvised glyph mode":"Saved spell mode");
    }
    public static VoiceProfile profile(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null)return null;
        UUID id=mc.player.getUUID();
        if(!id.equals(loadedId)){
            loadedId=id;
            profile=VoiceProfile.load(id);
        }
        return profile;
    }
    private static void announce(String message){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player!=null)mc.player.displayClientMessage(Component.literal("[Voice Magic] "+message),true);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null){
            cancel=true;captureSession++;
            if(mic!=null)mic.stop();
            capturing=false;finishing=false;wasCastingKeyDown=false;
            preview=List.of();spoken.clear();profile=null;loadedId=null;return;
        }
        profile();
        while(BOOK.consumeClick()){
            if(mc.screen==null&&!capturing&&!finishing){
                InteractionHand hand=ArsVoiceSupport.findBook(mc.player);
                if(hand!=null)mc.setScreen(new ArsVoiceBookScreen(new GuiSpellBook(hand)));
                else announce("Hold an Ars Nouveau spellbook to open voice training");
            }
        }
        while(MODE.consumeClick())if(mc.screen==null)toggleMode();
        if(mc.screen!=null){
            if(capturing&&mic!=null){cancel=true;captureSession++;mic.stop();capturing=false;}
            preview=List.of();wasCastingKeyDown=false;return;
        }
        while(UNDO.consumeClick())if(capturing){
            if(!spoken.isEmpty())spoken.remove(spoken.size()-1);
            preview=List.of();
        }
        boolean down=CAST.isDown();
        if(down&&!wasCastingKeyDown&&!capturing&&!finishing)start();
        else if(!down&&wasCastingKeyDown&&capturing&&mic!=null){
            capturing=false;finishing=true;
            preview=List.of();smoothLevel=0;
            mic.stop();
        }
        wasCastingKeyDown=down;
    }

    private static void start(){
        Minecraft mc=Minecraft.getInstance();
        VoiceProfile p=profile();
        if(mc.player==null||p==null)return;
        heldHand=ArsVoiceSupport.findBook(mc.player);
        if(heldHand==null){announce("Hold an Ars Nouveau spellbook in either hand");return;}
        AbstractCaster<?> caster=ArsVoiceSupport.caster(mc.player,heldHand);
        if(caster==null){announce("Held book has no Ars spellcaster");return;}
        Set<String> allowed=new java.util.HashSet<>();
        if(liveMode){
            for(AbstractSpellPart part:ArsVoiceSupport.knownGlyphs(mc.player))
                allowed.add(ArsVoiceSupport.glyphKey(part));
        }else{
            for(int i=0;i<caster.getMaxSlots();i++){
                Spell spell=caster.getSpell(i);
                if(spell!=null&&!spell.isEmpty())allowed.add(ArsVoiceSupport.savedKey(spell));
            }
        }
        boolean trained=false;
        for(String id:allowed)if(p.count(id)>=5){trained=true;break;}
        if(!trained){
            announce("Open the Voice book and train "+(liveMode?"glyphs":"a saved spell"));
            return;
        }
        spoken.clear();preview=List.of();cancel=false;capturing=true;finishing=false;
        smoothLevel=0;
        final long session=++captureSession;
        final boolean usingGlyphs=liveMode;
        final InteractionHand castingHand=heldHand;
        final Set<String> accepted=Set.copyOf(allowed);

        mic=new MicCapture(false,new MicCapture.Listener(){
            @Override public void onPreview(short[] sample){
                if(!usingGlyphs||!previewBusy.compareAndSet(false,true))return;
                Thread worker=new Thread(()->{
                    try {
                        List<String> provisional=p.matchContinuous(sample,accepted::contains,20);
                        Minecraft.getInstance().execute(()->{
                            if(captureSession==session&&capturing&&CAST.isDown()&&!cancel)
                                preview=provisional;
                        });
                    }finally{previewBusy.set(false);}
                },"VoiceMagic-riff-preview");
                worker.setDaemon(true);worker.start();
            }
            @Override public void onUtterance(short[] sample){
                List<String> matched=new ArrayList<>();
                if(usingGlyphs) {
                    matched.addAll(p.matchContinuous(sample,accepted::contains,20));
                    // Keep the old single-word behavior as a fallback for short isolated words.
                    if(matched.isEmpty()){
                        VoiceProfile.Match m=p.match(sample,accepted::contains);
                        if(m!=null)matched.add(m.concept());
                    }
                }else{
                    VoiceProfile.Match m=p.match(sample,accepted::contains);
                    if(m!=null)matched.add(m.concept());
                }
                Minecraft.getInstance().execute(()->{
                    if(captureSession!=session||cancel||(!capturing&&!finishing))return;
                    preview=List.of();
                    int limit=usingGlyphs?20:1;
                    for(String id:matched)if(spoken.size()<limit)spoken.add(id);
                });
            }
            @Override public void onFinished(){
                Minecraft.getInstance().execute(()->{
                    if(captureSession!=session)return;
                    mic=null;capturing=false;finishing=false;preview=List.of();
                    if(!cancel){
                        List<String> completed=List.copyOf(spoken);
                        if(completed.isEmpty())announce("No incantation recognized");
                        else{
                            String request=buildCast(completed,castingHand,usingGlyphs);
                            if(request==null)announce("Spell unavailable or book changed");
                            else PacketDistributor.sendToServer(new ArsVoiceCastPayload(request));
                        }
                    }
                    spoken.clear();
                });
            }
            @Override public void onError(String error){
                Minecraft.getInstance().execute(()->announce("Microphone error: "+error));
            }
        });
        mic.start();
    }

    private static String buildCast(List<String> words,InteractionHand hand,boolean glyphs){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||hand==null||!ArsVoiceSupport.heldSpellbook(mc.player,hand))return null;
        if(glyphs){
            if(words.size()<2)return null;
            for(String word:words)if(!word.startsWith("glyph:"))return null;
            return "glyphs|"+hand.name()+"|-|"+String.join(",",words);
        }
        if(words.size()!=1||!words.getFirst().startsWith("saved:"))return null;
        AbstractCaster<?> caster=ArsVoiceSupport.caster(mc.player,hand);
        if(caster==null)return null;
        for(int i=0;i<caster.getMaxSlots();i++){
            Spell s=caster.getSpell(i);
            if(s!=null&&!s.isEmpty()&&ArsVoiceSupport.savedKey(s).equals(words.getFirst()))
                return "saved|"+hand.name()+"|"+i+"|"+words.getFirst();
        }
        return null;
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        Minecraft mc=Minecraft.getInstance();
        // Let go of ALT and the ring vanishes immediately, even if the mic is processing.
        if(mc.player==null||mc.options.hideGui||mc.screen!=null||!capturing||!CAST.isDown())return;
        GuiGraphics g=event.getGuiGraphics();
        int cx=g.guiWidth()/2,cy=g.guiHeight()/2+27;
        double volume=mic==null?0:mic.level();
        smoothLevel=smoothLevel*.62+volume*.38;
        double t=System.nanoTime()*1e-9;
        float hue=(float)((t/24)%1.0);
        for(int ring=0;ring<2;ring++){
            int points=144;
            double radius=(ring==0?41:46)+smoothLevel*(ring==0?2.5:5);
            int size=ring==0?2:Math.max(2,(int)(2+smoothLevel*3));
            int alpha=ring==0?190:115;
            for(int i=0;i<points;i++){
                double angle=i*2*Math.PI/points-Math.PI/2;
                float shade=(hue+(float)i/points*.075f)%1.0f;
                int rgb=Color.HSBtoRGB(shade,.54f,ring==0?.97f:.84f)&0xFFFFFF;
                int px=cx+(int)Math.round(Math.cos(angle)*radius);
                int py=cy+(int)Math.round(Math.sin(angle)*radius);
                g.fill(px-size/2,py-size/2,px+size/2+1,py+size/2+1,(alpha<<24)|rgb);
            }
        }
        List<String> icons=new ArrayList<>(spoken);
        if(!preview.isEmpty()&&liveMode){
            for(String word:preview)if(icons.size()<20)icons.add(word);
        }
        if(liveMode)drawGlyphs(g,icons,cx,cy);
        else if(!icons.isEmpty())drawSavedSpell(g,icons.getFirst(),cx,cy);
    }

    private static void drawGlyphs(GuiGraphics g,List<String> glyphs,int cx,int cy){
        int count=Math.min(glyphs.size(),20);
        if(count==0)return;
        double radius=59.0;
        for(int i=0;i<count;i++){
            String key=glyphs.get(i);
            if(!key.startsWith("glyph:"))continue;
            ResourceLocation id=ResourceLocation.tryParse(key.substring(6));
            AbstractSpellPart part=id==null?null:GlyphRegistry.getSpellPart(id);
            if(part==null)continue;
            double angle=-Math.PI/2+2*Math.PI*i/Math.max(8,count);
            int x=cx+(int)(radius*Math.cos(angle))-8;
            int y=cy+(int)(radius*Math.sin(angle))-8;
            g.renderItem(new ItemStack(part.getGlyph()),x,y);
        }
    }

    private static void drawSavedSpell(GuiGraphics g,String key,int cx,int cy){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null)return;
        AbstractCaster<?> caster=ArsVoiceSupport.caster(mc.player,heldHand);
        if(caster==null)return;
        for(int i=0;i<caster.getMaxSlots();i++){
            Spell spell=caster.getSpell(i);
            if(spell!=null&&!spell.isEmpty()&&ArsVoiceSupport.savedKey(spell).equals(key)){
                AbstractSpellPart form=spell.getCastMethod();
                if(form!=null)g.renderItem(new ItemStack(form.getGlyph()),cx-8,cy-16);
                String name=caster.getSpellName(i);
                if(name==null||name.isBlank())name="Spell "+(i+1);
                g.drawCenteredString(mc.font,fit(name,14),cx,cy+10,0xFFE7F4FC);
                break;
            }
        }
    }
    private static String fit(String s,int n){
        return s.length()>n?s.substring(0,n-3)+"...":s;
    }
}
