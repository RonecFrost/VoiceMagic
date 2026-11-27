package com.ronec.voicemagic.client;

import com.ronec.voicemagic.audio.MicCapture;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A five-sample guided training wizard. One click begins the complete enrollment session. */
public final class VoiceTrainerScreen extends Screen {
    private final Screen parent;
    private final String concept;
    private final String displayName;
    private EditBox word;
    private MicCapture mic;
    private Button recordButton;
    private String state = "Record five pronunciations of your chosen word.";
    private int sessionCount;
    private boolean cancelled;

    public VoiceTrainerScreen(Screen parent, String concept, String displayName) {
        super(Component.literal("Voice Magic - " + displayName));
        this.parent = parent;
        this.concept = concept;
        this.displayName = displayName;
    }

    @Override protected void init() {
        int cx = width / 2, y = height / 2 - 87;
        word = new EditBox(font, cx - 97, y + 38, 194, 20, Component.literal("Incantation label"));
        word.setMaxLength(24);
        VoiceProfile profile = VoiceClient.profile();
        word.setValue(profile == null ? displayName : profile.count(concept)==0 ? displayName : profile.alias(concept));
        addRenderableWidget(word);
        recordButton = addRenderableWidget(Button.builder(Component.literal("Start 5 recordings"), b -> toggleTraining())
                .bounds(cx - 97, y + 72, 194, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Forget recordings"), b -> {
            if (mic != null) return;
            VoiceProfile p = VoiceClient.profile();
            if (p != null) p.reset(concept);
            state = "Recordings erased. Choose a new word.";
        }).bounds(cx - 97, y + 105, 194, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back to Spellbook"), b -> onClose())
                .bounds(cx - 97, y + 155, 194, 20).build());
    }

    private void toggleTraining() {
        if (mic != null) { cancelled = true; mic.stop(); state = "Recording stopped."; return; }
        VoiceProfile p = VoiceClient.profile();
        if (p == null) return;
        String alias = word.getValue().strip();
        if (alias.isEmpty()) { state = "Enter a name for your chosen sound."; return; }
        if (p.count(concept) >= 5) {
            state = "Already trained. Choose Forget recordings to retrain.";
            return;
        }
        sessionCount = p.count(concept);
        cancelled = false;
        state = "Listening for pronunciation " + (sessionCount + 1) + "/5 - speak then pause.";
        recordButton.setMessage(Component.literal("Stop recording"));
        word.setEditable(false);
        mic = new MicCapture(false, new MicCapture.Listener() {
            @Override public void onUtterance(short[] sample) {
                Minecraft.getInstance().execute(() -> {
                    if (cancelled || VoiceTrainerScreen.this.minecraft == null || mic == null) return;
                    int before = p.count(concept);
                    if (before >= 5) return;
                    int count = p.teach(concept, alias, sample);
                    if (count == before) {
                        state = "Couldn't process that sample. Please try again.";
                        return;
                    }
                    sessionCount = count;
                    if (count >= 5) {
                        state = "Training complete! Return to the world and hold Left Alt.";
                        mic.stop();
                    } else {
                        state = "Saved " + count + "/5. Say the word again after a brief pause.";
                    }
                });
            }
            @Override public void onFinished() {
                Minecraft.getInstance().execute(() -> {
                    if (VoiceTrainerScreen.this.minecraft == null) return;
                    mic = null;
                    word.setEditable(true);
                    recordButton.setMessage(Component.literal("Start 5 recordings"));
                    if (!cancelled && sessionCount < 5 && !state.contains("error"))
                        state = "Mic stopped early. " + sessionCount + "/5 saved; restart to continue.";
                });
            }
            @Override public void onError(String error) {
                Minecraft.getInstance().execute(() -> state = "Microphone error: " + error);
            }
        });
        mic.start();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int cx = width / 2, y = height / 2 - 87;
        g.fill(cx - 121, y - 34, cx + 121, y + 208, 0xE0171C28);
        g.fill(cx - 118, y - 31, cx + 118, y + 205, 0xFF253044);
        g.drawCenteredString(font, fit(displayName.toUpperCase(Locale.ROOT),36), cx, y - 20, 0xFFFFFFFF);
        g.drawCenteredString(font, "Ars Nouveau spell or glyph - training only", cx, y - 6, 0xFFADC6E2);
        g.drawString(font, "Your personal spoken sound:", cx - 97, y + 27, 0xFFE3EDF8);
        int count = VoiceClient.profile() == null ? 0 : VoiceClient.profile().count(concept);
        g.drawCenteredString(font, "Voice samples: " + count + " / 5", cx, y + 137, 0xFF98F8D7);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, fit(state, 56), cx, y + 185, 0xFFE4EAF7);
        if (mic != null && mic.active()) {
            for (int i = 0; i < 25; i++) {
                int h = (int)(2 + mic.level() * (5 + 13 * Math.abs(Math.sin(i * 1.7))));
                g.fill(cx - 61 + i * 5, y + 197 - h, cx - 59 + i * 5, y + 197, 0xFF9DDDD9);
            }
        }
    }

    private static String fit(String s, int limit) {
        return s.length() <= limit ? s : s.substring(0, limit - 3) + "...";
    }

    @Override public void onClose() {
        cancelled = true;
        if (mic != null) mic.stop();
        minecraft.setScreen(parent);
    }
}
