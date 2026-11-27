package com.ronec.voicemagic.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A virtual spellbook that is available immediately, without equipment or unlocks. */
public final class VoiceJournalScreen extends Screen {
    public VoiceJournalScreen() { super(Component.literal("Voice Magic - Spellbook")); }

    @Override protected void init() {
        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.literal("Projectile"), b ->
                minecraft.setScreen(new VoiceTrainerScreen(this)))
                .bounds(cx - 110, height / 2 - 27, 220, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Close Spellbook"), b -> onClose())
                .bounds(cx - 75, height / 2 + 79, 150, 20).build());
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int cx = width / 2, cy = height / 2;
        g.fill(cx - 140, cy - 107, cx + 140, cy + 112, 0xE0251B18);
        g.fill(cx - 132, cy - 99, cx + 132, cy + 104, 0xFFF1E6C9);
        g.fill(cx, cy - 99, cx + 1, cy + 104, 0xFFBCAD8C);
        g.drawCenteredString(font, "THE SPELLBOOK", cx, cy - 86, 0xFF4E3228);
        g.drawString(font, "KNOWN SPELLS", cx - 110, cy - 51, 0xFF71573A);
        super.render(g, mouseX, mouseY, partialTick);
        VoiceProfile p = VoiceClient.profile();
        int count = p == null ? 0 : p.count("projectile");
        g.drawCenteredString(font, count == 5 ? "Incantation trained: " + p.alias("projectile")
                : "Click Projectile to train its incantation (" + count + "/5)", cx, cy + 18, 0xFF584837);
        g.drawCenteredString(font, "No research or equipment required", cx, cy + 41, 0xFF6F5B44);
        g.drawCenteredString(font, "Hold LEFT ALT, speak, then release to cast", cx, cy + 55, 0xFF6F5B44);
    }
}
