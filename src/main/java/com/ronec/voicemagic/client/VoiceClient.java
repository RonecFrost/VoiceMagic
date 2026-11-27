package com.ronec.voicemagic.client;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractCaster;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
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
import com.mojang.math.Axis;
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
                if(!ArsVoiceSupport.inventoryBooks(mc.player).isEmpty())
                    mc.setScreen(new ArsVoiceBookScreen(null));
                else announce("Carry an Ars Nouveau spellbook to open voice training");
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
        var books=ArsVoiceSupport.inventoryBooks(mc.player);
        if(books.isEmpty()){announce("Carry an Ars Nouveau spellbook to cast by voice");return;}
        Set<String> allowed=new java.util.HashSet<>();
        if(liveMode){
            for(AbstractSpellPart part:ArsVoiceSupport.knownGlyphs(mc.player))
                allowed.add(ArsVoiceSupport.glyphKey(part));
        }else{
            for(var book:books){
                AbstractCaster<?> caster=book.caster();
                if(caster==null)continue;
                for(int i=0;i<caster.getMaxSlots();i++){
                    Spell spell=caster.getSpell(i);
                    if(spell!=null&&!spell.isEmpty())allowed.add(ArsVoiceSupport.savedKey(spell));
                }
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
                            String request=buildCast(completed,usingGlyphs);
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

    private static String buildCast(List<String> words,boolean glyphs){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||ArsVoiceSupport.inventoryBooks(mc.player).isEmpty())return null;
        if(glyphs){
            if(words.size()<2)return null;
            for(String word:words)if(!word.startsWith("glyph:"))return null;
            return "glyphs|inventory|-|"+String.join(",",words);
        }
        if(words.size()!=1||!words.getFirst().startsWith("saved:"))return null;
        var saved=ArsVoiceSupport.findSavedSpell(mc.player,words.getFirst());
        return saved==null?null:"saved|inventory|-|"+words.getFirst();
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

        // Two equal semicircles of thin radial bars. Their endpoints meet at
        // top and bottom, while each bar reacts to the live microphone energy.
        double t=System.nanoTime()*1e-9;
        float hue=(float)((t/38.0)%1.0);
        final int barsPerHalf=72;
        final double baseline=54.0;
        for(int half=0;half<2;half++){
            for(int i=0;i<=barsPerHalf;i++){
                double u=(double)i/barsPerHalf;
                double theta=-Math.PI/2+(half==0?1:-1)*Math.PI*u;
                // Ends taper cleanly so both semicircles join without spikes.
                double taper=Math.pow(Math.max(0,Math.sin(Math.PI*u)),.74);
                double modulation=.55 + .28*Math.sin(u*19*Math.PI-t*9.0+half*.7)
                        + .17*Math.sin(u*36*Math.PI+t*5.5+half*1.3);
                modulation=Math.max(.12,Math.min(1.0,modulation));
                double length=2.0+taper*(3.0+(9.0+smoothLevel*33.0)*modulation);
                int inner=(int)Math.round(baseline-length*.45);
                int outer=(int)Math.round(baseline+length*.55);
                float shade=(hue+(float)(u*.08)+half*.014f)%1.0f;
                int rgb=Color.HSBtoRGB(shade,.6f,.98f)&0xFFFFFF;
                int color=0xE6000000|rgb;
                g.pose().pushPose();
                g.pose().translate(cx,cy,0);
                g.pose().mulPose(Axis.ZP.rotationDegrees((float)Math.toDegrees(theta+Math.PI/2)));
                g.fill(-1,-outer,1,-inner,color);
                g.pose().popPose();
            }
        }
        if(liveMode)drawGlyphs(g,spoken,cx,cy);
        else if(!spoken.isEmpty())drawSavedSpell(g,spoken.getFirst(),cx,cy);
    }

    private static void drawGlyphs(GuiGraphics g,List<String> glyphs,int cx,int cy){
        int count=Math.min(glyphs.size(),20);
        if(count==0)return;
        double radius=97.0;
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
        var found=ArsVoiceSupport.findSavedSpell(mc.player,key);
        if(found==null)return;
        Spell spell=found.spell();
        AbstractSpellPart form=spell.getCastMethod();
        if(form!=null)g.renderItem(new ItemStack(form.getGlyph()),cx-8,cy-16);
        AbstractCaster<?> caster=found.book().caster();
        String name=caster==null?"Spell":caster.getSpellName(found.slot());
        if(name==null||name.isBlank())name="Spell "+(found.slot()+1);
        g.drawCenteredString(mc.font,fit(name,14),cx,cy+10,0xFFE7F4FC);
    }
    private static String fit(String s,int n){
        return s.length()>n?s.substring(0,n-3)+"...":s;
    }
}
