package com.ronec.voicemagic.client;

import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.ronec.voicemagic.VoiceMagicMod;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Adds a small Voice tab to the original Ars Nouveau spell-crafting GUI. */
@EventBusSubscriber(modid=VoiceMagicMod.MOD_ID,value=Dist.CLIENT)
public final class ArsGuiHook {
    private ArsGuiHook() {}
    @SubscribeEvent public static void addVoiceTab(ScreenEvent.Init.Post event) {
        if(event.getScreen() instanceof GuiSpellBook ars) {
            event.addListener(Button.builder(Component.literal("Voice"),
                    b->net.minecraft.client.Minecraft.getInstance().setScreen(new ArsVoiceBookScreen(ars)))
                    .bounds(ars.bookLeft+135,ars.bookTop-21,70,19).build());
        }
    }
}
