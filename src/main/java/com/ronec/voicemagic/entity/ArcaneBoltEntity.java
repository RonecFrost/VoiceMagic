package com.ronec.voicemagic.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Small, gravity-free, damage-dealing bolt. The custom translucent renderer supplies its appearance. */
public final class ArcaneBoltEntity extends ThrowableItemProjectile {
    public ArcaneBoltEntity(EntityType<? extends ArcaneBoltEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public ArcaneBoltEntity(Level level, LivingEntity caster) {
        this(ModEntities.ARCANE_BOLT.get(), level);
        setOwner(caster);
        setPos(caster.getX(), caster.getEyeY() - 0.1, caster.getZ());
    }

    @Override protected Item getDefaultItem() { return Items.AMETHYST_SHARD; }
    @Override protected double getDefaultGravity() { return 0.0; }

    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!level().isClientSide) {
            hit.getEntity().hurt(level().damageSources().thrown(this, getOwner()), 4.0F);
        }
    }

    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide) discard();
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 80) discard();
    }
}
