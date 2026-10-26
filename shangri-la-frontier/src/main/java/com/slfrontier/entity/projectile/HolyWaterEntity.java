package com.slfrontier.entity.projectile;

import com.slfrontier.registry.ModEffects;
import com.slfrontier.registry.ModEntities;
import com.slfrontier.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Thrown Holy Water. Burns undead and drenches them, which quenches Wezaemon's grave aura. */
public class HolyWaterEntity extends ThrowableItemProjectile {
    public HolyWaterEntity(EntityType<? extends HolyWaterEntity> type, Level level) {
        super(type, level);
    }

    public HolyWaterEntity(Level level, LivingEntity owner) {
        super(ModEntities.HOLY_WATER.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.HOLY_WATER.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel server)) return;
        server.sendParticles(ParticleTypes.SPLASH, getX(), getY(), getZ(), 60, 1.2, 0.5, 1.2, 0.2);
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 20, 1.0, 0.6, 1.0, 0.05);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.NEUTRAL, 1f, 1.2f);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5, 2, 3.5))) {
            e.clearFire();
            if (e.getType().is(EntityTypeTags.UNDEAD)) {
                e.hurt(damageSources().indirectMagic(this, getOwner()), 8f);
                e.addEffect(new MobEffectInstance(ModEffects.DRENCHED, 20 * 15, 0), getOwner());
            }
        }
        discard();
    }
}
