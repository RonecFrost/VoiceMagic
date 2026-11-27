package com.ronec.voicemagic.client;

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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;

/** Offline voice recognition; vanilla Ars Nouveau casts are requested only on release. */
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
    private static String status="";
    private static int messageTicks;
    private VoiceClient() {}

    @EventBusSubscriber(modid=VoiceMagicMod.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class ModKeys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) {
            e.register(CAST);e.register(BOOK);e.register(MODE);e.register(UNDO);
        }
    }

    public static boolean isLiveMode() {return liveMode;}
    public static void toggleMode() {
        if(capturing||finishing)return;
        liveMode=!liveMode;
        message(liveMode?"Live glyph mode enabled":"Saved spell mode enabled");
    }

    public static VoiceProfile profile() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null)return null;
        UUID id=mc.player.getUUID();
        if(!id.equals(loadedId)) {
            loadedId=id;
            profile=VoiceProfile.load(id);
        }
        return profile;
    }

    private static void message(String m){status=m;messageTicks=120;}

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null){
            cancel=true;
            if(mic!=null)mic.stop();
            capturing=false;finishing=false;wasCastingKeyDown=false;
            spoken.clear();profile=null;loadedId=null;return;
        }
        profile();
        while(BOOK.consumeClick()) {
            if(mc.screen==null&&!capturing&&!finishing) {
                InteractionHand hand=ArsVoiceSupport.findBook(mc.player);
                if(hand!=null)mc.setScreen(new ArsVoiceBookScreen(new GuiSpellBook(hand)));
                else message("Hold an Ars Nouveau spellbook to open voice training");
            }
        }
        while(MODE.consumeClick()) if(mc.screen==null)toggleMode();
        if(messageTicks>0)messageTicks--;
        if(mc.screen!=null) {
            if(capturing&&mic!=null){cancel=true;mic.stop();capturing=false;}
            wasCastingKeyDown=false;return;
        }
        while(UNDO.consumeClick()) {
            if(capturing&&!spoken.isEmpty()) {
                String removed=spoken.remove(spoken.size()-1);
                message("Removed last word: "+aliasFor(removed));
            }
        }
        boolean down=CAST.isDown();
        if(down&&!wasCastingKeyDown&&!capturing&&!finishing)start();
        else if(!down&&wasCastingKeyDown&&capturing&&mic!=null){
            capturing=false;finishing=true;mic.stop();
        }
        wasCastingKeyDown=down;
    }

    private static String aliasFor(String id){
        VoiceProfile p=profile();
        return p==null?id:p.alias(id);
    }

    private static void start() {
        Minecraft mc=Minecraft.getInstance();
        VoiceProfile p=profile();
        if(mc.player==null||p==null)return;
        heldHand=ArsVoiceSupport.findBook(mc.player);
        if(heldHand==null){message("Hold an Ars Nouveau spellbook in either hand");return;}
        AbstractCaster<?> caster=ArsVoiceSupport.caster(mc.player,heldHand);
        if(caster==null){message("Your held item has no Ars spellcaster");return;}
        Set<String> allowed=new java.util.HashSet<>();
        if(liveMode){
            for(AbstractSpellPart part:ArsVoiceSupport.knownGlyphs(mc.player))
                allowed.add(ArsVoiceSupport.glyphKey(part));
        }else{
            for(int i=0;i<caster.getMaxSlots();i++){
                Spell s=caster.getSpell(i);
                if(s!=null&&!s.isEmpty())allowed.add(ArsVoiceSupport.savedKey(s));
            }
        }
        boolean hasTrained=false;
        for(String id:allowed)if(p.count(id)>=5){hasTrained=true;break;}
        if(!hasTrained) {
            message("Open the Ars Voice tab and train "+(liveMode?"glyphs":"a saved spell")+" first");
            return;
        }
        spoken.clear();cancel=false;capturing=true;finishing=false;
        final boolean usingGlyphs=liveMode;
        final InteractionHand castingHand=heldHand;
        final Set<String> accepted=Set.copyOf(allowed);
        mic=new MicCapture(false,new MicCapture.Listener(){
            @Override public void onUtterance(short[] sample){
                VoiceProfile.Match match=p.match(sample,accepted::contains);
                Minecraft.getInstance().execute(()->{
                    if(cancel||(!capturing&&!finishing))return;
                    if(match==null){message("Word not recognized - retry");return;}
                    int maximum=usingGlyphs?20:1;
                    if(spoken.size()>=maximum){
                        message("Voice slots full - undo or release to cast");return;
                    }
                    spoken.add(match.concept());
                    message("Recognized: "+p.alias(match.concept()));
                });
            }
            @Override public void onFinished(){
                Minecraft.getInstance().execute(()->{
                    mic=null;capturing=false;finishing=false;
                    if(!cancel){
                        List<String> completed=List.copyOf(spoken);
                        if(completed.isEmpty())message("No incantation recognized");
                        else {
                            String request=buildCast(completed,castingHand,usingGlyphs);
                            if(request==null)message("Spell unavailable, invalid, or book changed");
                            else{
                                PacketDistributor.sendToServer(new ArsVoiceCastPayload(request));
                                message(usingGlyphs?"Casting improvised glyph spell":"Casting trained Ars spell");
                            }
                        }
                    }
                    spoken.clear();
                });
            }
            @Override public void onError(String error) {
                Minecraft.getInstance().execute(()->message("Microphone error: "+error));
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
            if(s!=null&&!s.isEmpty()&&ArsVoiceSupport.savedKey(s).equals(words.getFirst())){
                return "saved|"+hand.name()+"|"+i+"|"+words.getFirst();
            }
        }
        return null;
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.options.hideGui||mc.screen!=null)return;
        if(!capturing&&!finishing&&messageTicks<=0)return;
        GuiGraphics g=e.getGuiGraphics();
        int cx=g.guiWidth()/2,y=g.guiHeight()/2+27;
        int rows=Math.min(5,Math.max(1,(spoken.size()+3)/4));
        int bottom=y+47+rows*19;
        g.fill(cx-137,y-11,cx+137,bottom+4,0xD01B2536);
        String mode=liveMode?"GLYPHS / IMPROVISE":"SAVED SPELLS";
        g.drawCenteredString(mc.font,capturing?"LISTENING - "+mode:finishing?"Processing speech...":fit(status,44),
            cx,y,0xFFF0F5FF);
        if(capturing||finishing){
            double level=mic==null?0:mic.level();
            for(int i=0;i<35;i++){
                int h=(int)(3+level*(5+12*Math.abs(Math.sin(i*1.81))));
                g.fill(cx-88+i*5,y+23-h/2,cx-86+i*5,y+23+h/2,0xFF78D5EA);
            }
            for(int i=0;i<spoken.size()&&i<20;i++){
                int x=cx-129+(i%4)*65,dy=y+39+(i/4)*19;
                g.fill(x,dy,x+62,dy+16,0xBB40516B);
                g.drawCenteredString(mc.font,fit(aliasFor(spoken.get(i)),9),x+31,dy+4,0xFFBFFFF3);
            }
            g.drawCenteredString(mc.font,"BACKSPACE: undo     Release ALT: cast",cx,bottom-7,0xFFC6D9EC);
        }
    }
    private static String fit(String s,int len){
        return s.length()<=len?s:s.substring(0,Math.max(0,len-3))+"...";
    }
}
