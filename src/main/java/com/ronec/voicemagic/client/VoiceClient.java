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

/** Isolated spoken glyphs, a crosshair-centered reactive waveform, and native Ars casting. */
@EventBusSubscriber(modid=VoiceMagicMod.MOD_ID,value=Dist.CLIENT)
public final class VoiceClient {
    private static final KeyMapping CAST=new KeyMapping("key.voicemagic.cast",KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_ALT,"key.categories.voicemagic");
    private static final KeyMapping BOOK=new KeyMapping("key.voicemagic.book",InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,"key.categories.voicemagic");
    private static final KeyMapping MODE=new KeyMapping("key.voicemagic.mode",KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_SEMICOLON,"key.categories.voicemagic");
    private static final KeyMapping UNDO=new KeyMapping("key.voicemagic.undo",KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_CONTROL,"key.categories.voicemagic");

    private static UUID loadedId;
    private static VoiceProfile profile;
    private static MicCapture mic;
    private static boolean capturing,finishing,cancel,wasCastingKeyDown,liveMode;
    private static InteractionHand heldHand;
    private static final List<String> spoken=new ArrayList<>();
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
            spoken.clear();profile=null;loadedId=null;return;
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
            wasCastingKeyDown=false;return;
        }
        while(UNDO.consumeClick())if(capturing){
            if(!spoken.isEmpty())spoken.remove(spoken.size()-1);
            
        }
        boolean down=CAST.isDown();
        if(down&&!wasCastingKeyDown&&!capturing&&!finishing)start();
        else if(!down&&wasCastingKeyDown&&capturing&&mic!=null){
            capturing=false;finishing=true;
            smoothLevel=0;
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
        spoken.clear();cancel=false;capturing=true;finishing=false;
        smoothLevel=0;
        final long session=++captureSession;
        final boolean usingGlyphs=liveMode;
        final InteractionHand castingHand=heldHand;
        final Set<String> accepted=Set.copyOf(allowed);

        mic=new MicCapture(false,new MicCapture.Listener(){
            @Override public void onUtterance(short[] sample){
                // One utterance is matched against ONE trained glyph, exactly
                // as before the experimental connected-speech decoder.
                VoiceProfile.Match match=p.match(sample,accepted::contains);
                Minecraft.getInstance().execute(()->{
                    if(captureSession!=session||cancel||(!capturing&&!finishing))return;
                    if(match==null)return; // ambiguous sound: try the word again
                    int limit=usingGlyphs?20:1;
                    if(spoken.size()<limit)spoken.add(match.concept());
                });
            }
            @Override public void onFinished(){
                Minecraft.getInstance().execute(()->{
                    if(captureSession!=session)return;
                    mic=null;capturing=false;finishing=false;
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
        // Nothing remains on screen when ALT is released.
        if(mc.player==null||mc.options.hideGui||mc.screen!=null||!capturing||!CAST.isDown())return;
        GuiGraphics g=event.getGuiGraphics();
        int cx=g.guiWidth()/2;
        int cy=g.guiHeight()/2; // align with the actual center crosshair

        double volume=mic==null?0:mic.level();
        // Old ring used only a few pixels of movement; significantly amplify
        // quiet microphone levels, but smooth between rendered frames.
        double input=Math.min(1.0,Math.pow(Math.min(1.0,volume*4.8),.68));
        smoothLevel=smoothLevel*.43+input*.57;

        // One closed circle consisting of two independent waveform semicircles.
        // Right and left share exactly the top/bottom endpoints, with an
        // envelope which approaches zero at those points.
        double t=System.nanoTime()*1e-9;
        float hue=(float)((t/35.0)%1.0);
        final int segments=112;
        final double baseRadius=40;
        for(int half=0;half<2;half++){
            int priorX=0,priorY=0;
            boolean hasPrevious=false;
            for(int i=0;i<=segments;i++){
                double u=(double)i/segments;
                double angle=-Math.PI/2+(half==0?1:-1)*Math.PI*u;
                double envelope=Math.pow(Math.sin(Math.PI*u),.75);
                double waveform=Math.sin(u*17*Math.PI-t*13.0+half*.32)*.79
                    +Math.sin(u*33*Math.PI+t*8.5+half*1.20)*.21;
                double displacement=(1.35+smoothLevel*23.5)*envelope*waveform;
                double radius=baseRadius+displacement;
                int px=cx+(int)Math.round(Math.cos(angle)*radius);
                int py=cy+(int)Math.round(Math.sin(angle)*radius);
                float shade=(hue+(float)u*.09f+half*.012f)%1.0f;
                int rgb=Color.HSBtoRGB(shade,.62f,.99f)&0xFFFFFF;
                int argb=(225<<24)|rgb;
                // Interpolate pixel stamps so even energetic waves remain
                // connected and don't become disconnected dots.
                if(hasPrevious){
                    int steps=Math.max(Math.abs(px-priorX),Math.abs(py-priorY));
                    for(int d=0;d<=steps;d++){
                        double f=steps==0?0:(double)d/steps;
                        int xx=(int)Math.round(priorX+(px-priorX)*f);
                        int yy=(int)Math.round(priorY+(py-priorY)*f);
                        g.fill(xx-1,yy-1,xx+2,yy+2,argb);
                    }
                }else{
                    g.fill(px-1,py-1,px+2,py+2,argb);
                }
                priorX=px;priorY=py;hasPrevious=true;
            }
        }
        if(liveMode)drawGlyphs(g,spoken,cx,cy);
        else if(!spoken.isEmpty())drawSavedSpell(g,spoken.getFirst(),cx,cy);
    }

    private static void drawGlyphs(GuiGraphics g,List<String> glyphs,int cx,int cy){
        int count=Math.min(glyphs.size(),20);
        if(count==0)return;
        double radius=69.0;
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
