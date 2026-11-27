package com.ronec.voicemagic.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ronec.voicemagic.SpellConcepts;
import com.ronec.voicemagic.VoiceMagicMod;
import com.ronec.voicemagic.audio.MicCapture;
import com.ronec.voicemagic.network.CastPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Continuous mic capture. Individual recognized words fill the three concept slots. */
@EventBusSubscriber(modid=VoiceMagicMod.MOD_ID,value=Dist.CLIENT)
public final class VoiceClient {
    private static final KeyMapping CAST=new KeyMapping("key.voicemagic.cast",KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_ALT,"key.categories.voicemagic");
    private static final KeyMapping BOOK=new KeyMapping("key.voicemagic.book",InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,"key.categories.voicemagic");
    private static final KeyMapping UNDO=new KeyMapping("key.voicemagic.undo",KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_BACKSPACE,"key.categories.voicemagic");

    private static UUID loadedId;
    private static VoiceProfile profile;
    private static MicCapture mic;
    private static boolean capturing,finishing,cancel,wasCastingKeyDown;
    private static final List<String> spoken=new ArrayList<>();
    private static String status="";
    private static int messageTicks;
    private VoiceClient() {}

    @EventBusSubscriber(modid=VoiceMagicMod.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class ModKeys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) {
            event.register(CAST);event.register(BOOK);event.register(UNDO);
        }
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

    private static void message(String s) {status=s;messageTicks=110;}

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null) {
            cancel=true;
            if(mic!=null)mic.stop();
            capturing=false;finishing=false;wasCastingKeyDown=false;
            spoken.clear();profile=null;loadedId=null;
            return;
        }
        profile();
        while(BOOK.consumeClick()) {
            if(mc.screen==null&&!capturing&&!finishing)mc.setScreen(new VoiceJournalScreen());
        }
        if(messageTicks>0)messageTicks--;
        if(mc.screen!=null) {
            if(capturing&&mic!=null) {cancel=true;mic.stop();capturing=false;}
            wasCastingKeyDown=false;
            return;
        }
        while(UNDO.consumeClick()) {
            if(capturing&&!spoken.isEmpty()) {
                String removed=spoken.remove(spoken.size()-1);
                message("Undid: "+SpellConcepts.label(removed));
            }
        }
        boolean keyDown=CAST.isDown();
        if(keyDown&&!wasCastingKeyDown&&!capturing&&!finishing)startCast();
        else if(!keyDown&&wasCastingKeyDown&&capturing&&mic!=null) {
            capturing=false;finishing=true;mic.stop();
        }
        wasCastingKeyDown=keyDown;
    }

    private static void startCast() {
        VoiceProfile p=profile();
        if(p==null||p.learned().isEmpty()) {
            message("Press V to train a spoken concept first");
            return;
        }
        spoken.clear();cancel=false;capturing=true;finishing=false;
        mic=new MicCapture(false,new MicCapture.Listener() {
            @Override public void onUtterance(short[] audio) {
                VoiceProfile.Match match=p.match(audio);
                Minecraft.getInstance().execute(()->{
                    if(cancel||(!capturing&&!finishing))return;
                    if(match==null) {
                        message("Unknown sound - repeat or use BACKSPACE");
                        return;
                    }
                    if(spoken.size()>=3) {
                        message("Three concept slots full - release ALT or undo");
                        return;
                    }
                    spoken.add(match.concept());
                    message("Recognized: "+p.alias(match.concept()));
                });
            }
            @Override public void onFinished() {
                Minecraft.getInstance().execute(()->{
                    mic=null;capturing=false;finishing=false;
                    if(!cancel) {
                        List<String> finished=List.copyOf(spoken);
                        if(SpellConcepts.validCast(finished)) {
                            PacketDistributor.sendToServer(new CastPayload(String.join(",",finished)));
                            message("Casting: "+finished.stream().map(SpellConcepts::label)
                                .reduce((a,b)->a+" + "+b).orElse(""));
                        } else if(!finished.isEmpty())
                            message("Incomplete/invalid spell: see spellbook for word order");
                        else message("No incantation recognized");
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

    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.options.hideGui||mc.screen!=null)return;
        if(!capturing&&!finishing&&messageTicks<=0)return;
        GuiGraphics g=e.getGuiGraphics();
        int x=g.guiWidth()/2,y=g.guiHeight()/2+28;
        g.fill(x-128,y-10,x+128,y+66,0xC0192434);
        g.drawCenteredString(mc.font,capturing?"LISTENING - HOLD ALT":finishing?"Processing...":fit(status,46),
            x,y-2,0xFFEBF6FF);
        if(capturing||finishing) {
            double level=mic==null?0:mic.level();
            for(int i=0;i<33;i++) {
                int h=(int)(2+level*(5+13*Math.abs(Math.sin(i*1.72))));
                g.fill(x-80+i*5,y+20-h/2,x-78+i*5,y+20+h/2,0xFF83D9F0);
            }
            VoiceProfile p=profile();
            for(int i=0;i<3;i++) {
                int left=x-112+i*76;
                g.fill(left,y+35,left+73,y+53,0xB537485B);
                String s=i<spoken.size()?(p==null?SpellConcepts.label(spoken.get(i)):p.alias(spoken.get(i))):"["+ (i+1) +"]";
                g.drawCenteredString(mc.font,fit(s,12),left+36,y+40,
                    i<spoken.size()?0xFFBFFFEF:0xFFBAC5D3);
            }
            g.drawCenteredString(mc.font,"BACKSPACE: undo  |  Release ALT: cast",x,y+56,0xFFE2EAF7);
        }
    }
    private static String fit(String s,int length) {
        return s.length()<=length?s:s.substring(0,length-3)+"...";
    }
}
