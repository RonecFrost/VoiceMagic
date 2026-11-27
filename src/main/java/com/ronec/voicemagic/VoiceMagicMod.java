package com.ronec.voicemagic;

import com.ronec.voicemagic.network.ArsVoiceCastPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(VoiceMagicMod.MOD_ID)
public final class VoiceMagicMod {
    public static final String MOD_ID="voicemagic";

    public VoiceMagicMod(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
    }
    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("4").playToServer(
                ArsVoiceCastPayload.TYPE, ArsVoiceCastPayload.STREAM_CODEC, ArsVoiceCastPayload::handle);
    }
}
