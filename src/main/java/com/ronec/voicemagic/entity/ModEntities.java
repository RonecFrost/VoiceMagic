package com.ronec.voicemagic.entity;

import com.ronec.voicemagic.VoiceMagicMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, VoiceMagicMod.MOD_ID);

    public static final Supplier<EntityType<ArcaneBoltEntity>> ARCANE_BOLT = TYPES.register(
            "arcane_bolt", () -> EntityType.Builder.<ArcaneBoltEntity>of(ArcaneBoltEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .build("voicemagic:arcane_bolt"));
}
