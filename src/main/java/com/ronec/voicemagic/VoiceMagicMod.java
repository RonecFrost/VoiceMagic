package com.ronec.voicemagic;

import com.ronec.voicemagic.entity.ModEntities;
import com.ronec.voicemagic.network.CastPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(VoiceMagicMod.MOD_ID)
public final class VoiceMagicMod {
    public static final String MOD_ID = "voicemagic";

    public VoiceMagicMod(IEventBus modBus) {
        ModEntities.TYPES.register(modBus);
        modBus.addListener(this::registerPayloads);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(CastPayload.TYPE, CastPayload.STREAM_CODEC, CastPayload::handle);
    }
}
