package com.ronec.voicemagic.client.render;

import com.ronec.voicemagic.VoiceMagicMod;
import com.ronec.voicemagic.entity.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid=VoiceMagicMod.MOD_ID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class ClientRenderRegistration {
    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ARCANE_BOLT.get(), ArcaneBoltRenderer::new);
    }
}
