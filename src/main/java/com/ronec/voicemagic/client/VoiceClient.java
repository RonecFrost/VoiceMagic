package com.ronec.voicemagic.client;

import com.mojang.blaze3d.platform.InputConstants;
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
import java.util.UUID;

/** UI + continuous hold-to-listen client; raw microphone audio stays on this machine. */
@EventBusSubscriber(modid = VoiceMagicMod.MOD_ID, value = Dist.CLIENT)
public final class VoiceClient {
    private static final KeyMapping CAST = new KeyMapping("key.voicemagic.cast", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.voicemagic");
    private static final KeyMapping BOOK = new KeyMapping("key.voicemagic.book", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, "key.categories.voicemagic");
    private static final KeyMapping UNDO = new KeyMapping("key.voicemagic.undo", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, "key.categories.voicemagic");

    private static UUID loadedId;
    private static VoiceProfile profile;
    private static MicCapture mic;
    private static boolean recognized, capturing, finishing, cancel, wasCastingKeyDown;
    private static String status = "";
    private static int messageTicks;
    private VoiceClient() {}

    @EventBusSubscriber(modid=VoiceMagicMod.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class ModKeys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) {
            event.register(CAST); event.register(BOOK); event.register(UNDO);
        }
    }

    public static VoiceProfile profile() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        UUID id = mc.player.getUUID();
        if (!id.equals(loadedId)) { loadedId = id; profile = VoiceProfile.load(id); }
        return profile;
    }

    private static void message(String text) { status = text; messageTicks = 100; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            cancel = true;
            if (mic != null) mic.stop();
            capturing = false; finishing = false; wasCastingKeyDown = false;
            profile = null; loadedId = null;
            return;
        }
        profile();
        while (BOOK.consumeClick()) {
            if (mc.screen == null && !capturing && !finishing) mc.setScreen(new VoiceJournalScreen());
        }
        if (messageTicks > 0) messageTicks--;
        if (mc.screen != null) {
            if (capturing && mic != null) { cancel = true; mic.stop(); capturing = false; }
            wasCastingKeyDown = false;
            return;
        }
        while (UNDO.consumeClick()) {
            if ((capturing || finishing) && recognized) { recognized = false; message("Last recognized word removed"); }
        }
        boolean keyDown = CAST.isDown();
        if (keyDown && !wasCastingKeyDown && !capturing && !finishing) startCast();
        else if (!keyDown && wasCastingKeyDown && capturing && mic != null) {
            capturing = false;
            finishing = true;
            mic.stop();
        }
        wasCastingKeyDown = keyDown;
    }

    private static void startCast() {
        VoiceProfile p = profile();
        if (p == null || p.count("projectile") < 5) {
            message("Press V > Projectile to train five samples first");
            return;
        }
        recognized = false;
        cancel = false;
        capturing = true;
        finishing = false;
        mic = new MicCapture(false, new MicCapture.Listener() {
            @Override public void onUtterance(short[] audio) {
                VoiceProfile.Match match = p.match(audio);
                Minecraft.getInstance().execute(() -> {
                    if (cancel) return;
                    if (match == null) { if (!recognized) message("Word not recognized - speak again"); return; }
                    if ("projectile".equals(match.concept())) {
                        recognized = true;
                        message("Recognized: " + p.alias("projectile"));
                    }
                });
            }
            @Override public void onFinished() {
                Minecraft.getInstance().execute(() -> {
                    mic = null;
                    capturing = false;
                    finishing = false;
                    if (!cancel && recognized) {
                        PacketDistributor.sendToServer(new CastPayload("projectile"));
                        message("Projectile cast!");
                    } else if (!cancel) message("No incantation recognized");
                    recognized = false;
                });
            }
            @Override public void onError(String error) {
                Minecraft.getInstance().execute(() -> message("Mic error: " + error));
            }
        });
        mic.start();
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        if (!capturing && !finishing && messageTicks <= 0) return;
        GuiGraphics g = event.getGuiGraphics();
        int x = g.guiWidth() / 2, y = g.guiHeight() / 2 + 29;
        g.fill(x - 112, y - 7, x + 112, y + 54, 0xBE192434);
        g.drawCenteredString(mc.font, capturing ? "LISTENING..." : finishing ? "Processing voice..." : status,
                x, y + 1, 0xFFEBF6FF);
        if (capturing || finishing) {
            double level = mic == null ? 0 : mic.level();
            for (int i = 0; i < 33; i++) {
                int h = (int)(3 + level * (5 + 14 * Math.abs(Math.sin(i * 1.72))));
                g.fill(x - 80 + i * 5, y + 30 - h/2, x - 78 + i * 5, y + 30 + h/2,
                        recognized ? 0xFFBFFFEF : 0xFF74CAEC);
            }
            g.drawCenteredString(mc.font, recognized ? "[Projectile] - release ALT to cast" :
                    "Say your word / BACKSPACE to correct", x, y + 43, 0xFFE2EAF7);
        }
    }
}
