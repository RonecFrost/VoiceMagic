package com.ronec.voicemagic.network;

import com.ronec.voicemagic.SpellConcepts;
import com.ronec.voicemagic.VoiceMagicMod;
import com.ronec.voicemagic.entity.ArcaneBoltEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server validates every action, selector, and value. Never execute arbitrary client command text. */
public record CastPayload(String spell) implements CustomPacketPayload {
    public static final Type<CastPayload> TYPE=new Type<>(
        ResourceLocation.fromNamespaceAndPath(VoiceMagicMod.MOD_ID,"cast"));
    public static final StreamCodec<ByteBuf,CastPayload> STREAM_CODEC=StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,CastPayload::spell,CastPayload::new);
    private static final Map<UUID,Long> LAST_CAST=new ConcurrentHashMap<>();

    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}

    public static void handle(CastPayload payload,IPayloadContext ctx) {
        if(!(ctx.player() instanceof ServerPlayer player))return;
        if(payload.spell()==null||payload.spell().length()>170)return;
        List<String> concepts=Arrays.asList(payload.spell().split(",",-1));
        if(!SpellConcepts.validCast(concepts)) {
            fail(player,"Invalid incantation. Use Projectile, or Action + Target + Value.");
            return;
        }
        long now=player.level().getGameTime();
        Long last=LAST_CAST.get(player.getUUID());
        if(last!=null&&now>=last&&now-last<7)return;
        LAST_CAST.put(player.getUUID(),now);

        if(concepts.size()==1) {
            if(player.isSpectator()) {fail(player,"Cannot fire a projectile while spectating.");return;}
            Vec3 dir=player.getLookAngle().normalize();
            ArcaneBoltEntity bolt=new ArcaneBoltEntity(player.level(),player);
            Vec3 origin=player.getEyePosition().add(dir.scale(.7));
            bolt.setPos(origin.x,origin.y-.1,origin.z);
            bolt.shoot(dir.x,dir.y,dir.z,1.75F,0.0F);
            player.level().addFreshEntity(bolt);
            return;
        }

        // These are deliberately administrative test spells, not a way around vanilla permissions.
        if(!player.hasPermissions(2)) {
            fail(player,"Gamemode/effect spells require cheats or operator level 2.");
            return;
        }

        String action=concepts.get(0),target=concepts.get(1),value=concepts.get(2);
        String selector=switch(target) {
            case "target:self" -> "@s";
            case "target:nearest" -> "@p";
            case "target:random" -> "@r";
            case "target:all_players" -> "@a";
            case "target:all_entities" -> "@e";
            default -> null;
        };
        if(selector==null)return;
        String command;
        if("cmd:gamemode".equals(action)) {
            if("target:all_entities".equals(target)) {
                fail(player,"Gamemode can only target players; @e includes other entities.");
                return;
            }
            command="gamemode "+value.substring("gamemode:".length())+" "+selector;
        } else if("cmd:effect".equals(action)) {
            String effect=value.substring("effect:".length());
            // 30 seconds at level I by default. Instant effects activate immediately.
            command="effect give "+selector+" minecraft:"+effect+" 30 0 true";
        } else return;

        // The command dispatcher validates the remaining vanilla restrictions as the actual player.
        if(player.getServer()!=null)
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(),command);
    }
    private static void fail(ServerPlayer player,String text) {
        player.sendSystemMessage(Component.literal("[Voice Magic] "+text));
    }
}
