package com.ronec.voicemagic.network;

import com.ronec.voicemagic.VoiceMagicMod;
import com.ronec.voicemagic.entity.ArcaneBoltEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A single permitted test spell, checked again on the authoritative server. */
public record CastPayload(String spell) implements CustomPacketPayload {
    public static final Type<CastPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VoiceMagicMod.MOD_ID, "cast"));
    public static final StreamCodec<ByteBuf, CastPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CastPayload::spell, CastPayload::new);
    private static final Map<UUID, Long> LAST_CAST = new ConcurrentHashMap<>();

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CastPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        if (!"projectile".equals(payload.spell()) || player.isSpectator()) return;
        long now = player.level().getGameTime();
        Long last = LAST_CAST.get(player.getUUID());
        if (last != null && now >= last && now - last < 7) return;
        LAST_CAST.put(player.getUUID(), now);

        Vec3 dir = player.getLookAngle().normalize();
        ArcaneBoltEntity bolt = new ArcaneBoltEntity(player.level(), player);
        Vec3 origin = player.getEyePosition().add(dir.scale(0.7));
        bolt.setPos(origin.x, origin.y - 0.1, origin.z);
        bolt.shoot(dir.x, dir.y, dir.z, 1.75F, 0.0F);
        player.level().addFreshEntity(bolt);
    }
}
