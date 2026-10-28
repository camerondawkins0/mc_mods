package com.slfrontier.entity.boss;

import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.entity.mob.ShadowWolfEntity;
import com.slfrontier.fx.Fx;
import com.slfrontier.quest.Story;
import com.slfrontier.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Lycagon's Shadow, which haunts the Lightless Barrens. It has simple patterns: it rises
 * out of a shadow near its prey, telegraphs and lands one attack, then sinks back into the
 * dark and cannot be harmed until it surfaces again. It is quicker on dark nights.
 *
 * <p>The same entity, in {@link #isClone() clone} mode, is the short-lived shadow that
 * the real Lycagon splits off in its second phase.
 */
public class LycagonShadowEntity extends FrontierBoss {
    public static final int BITE = 0, CLAW = 1, POUNCE = 2, HOWL = 3;
    private static final EntityDataAccessor<Boolean> SUBMERGED = SynchedEntityData.defineId(LycagonShadowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions AURA = new DustParticleOptions(new Vector3f(0.55f, 0.2f, 0.95f), 2.0f);
    private static final int PURPLE = 0xFFB070FF, PINK = 0xFFFF60E0;

    private boolean clone;
    private int submergedTicks = 40;
    private int lifetime = -1;
    private boolean summonedWolves;
    private Vec3 aim = Vec3.ZERO;

    public LycagonShadowEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 45, 1800, BossEvent.BossBarColor.PURPLE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 420)
                .add(Attributes.ATTACK_DAMAGE, 12)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SUBMERGED, true);
    }

    public boolean isSubmerged() {
        return entityData.get(SUBMERGED);
    }

    public boolean isClone() {
        return clone;
    }

    /** Makes this a weaker, short-lived clone of the real Lycagon. */
    public void makeClone(int lifetimeTicks) {
        clone = true;
        lifetime = lifetimeTicks;
        bossEvent.setVisible(false);
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(80);
        setHealth(80);
        submergedTicks = 10;
    }

    /** Dark nights (and storms) make the shadow faster. */
    private boolean darkNight() {
        return level().isNight() && (level().getMoonBrightness() < 0.5f || level().isRaining());
    }

    @Override
    protected int chooseAttack(LivingEntity target, double distance) {
        return -1;  // Attacks are driven by the surface/submerge cycle below.
    }

    @Override
    protected int attackLength(int attack) {
        return switch (attack) {
            case BITE -> 22;
            case CLAW -> 26;
            case POUNCE -> 28;
            case HOWL -> 30;
            default -> 10;
        };
    }

    @Override
    protected int animSlot(int attack) {
        return attack + 1;
    }

    @Override
    protected void tickAttack(int attack, int tick, LivingEntity target) {
        int warn = darkNight() ? 7 : 10;
        switch (attack) {
            case BITE -> {
                if (tick == 1) Fx.coneAt(level(), position(), getYRot(), 4.5f, 70, PURPLE, warn);
                if (tick == warn) hitAround(4.5, 1.0f, 0.3, 0.6);
            }
            case CLAW -> {
                if (tick == 1) Fx.coneAt(level(), position(), getYRot(), 5f, 140, PURPLE, warn + 2);
                if (tick == warn + 2) {
                    hitAround(5.0, 1.1f, -0.2, 0.9);
                    Fx.arc(this, 4.5f, 30, 160, PURPLE, 8);
                }
            }
            case POUNCE -> {
                if (tick == 1 && target != null) {
                    aim = target.position();
                    Fx.telegraph(level(), aim, 3.2f, PINK, warn + 4);
                }
                if (tick == warn) dashToward(aim, 1.2, 0.55);
                if (tick == warn + 4) {
                    hitAt(aim, 3.2, 1.3f, 1.0);
                    Fx.ring(level(), aim, 3.5f, PINK, 10, 0.4f);
                }
            }
            case HOWL -> {
                if (tick == 10) {
                    playSound(SoundEvents.WOLF_HOWL, 4f, 0.5f);
                    Fx.ring(level(), position(), 10f, PURPLE, 20, 0.3f);
                    summonWolves();
                }
            }
            default -> {
            }
        }
        if (tick == attackLength(attack) - 1) submerge();
    }

    private void submerge() {
        entityData.set(SUBMERGED, true);
        setInvisible(true);
        submergedTicks = (darkNight() ? 30 : 50) + random.nextInt(30);
        particles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 0.8, 0.02);
        playSound(SoundEvents.ENDERMAN_TELEPORT, 1f, 0.4f);
    }

    private void surface(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 spot = darkSpotNear(server, target);
        teleportTo(spot.x, spot.y, spot.z);
        lookAt(target, 360, 360);
        entityData.set(SUBMERGED, false);
        setInvisible(false);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 40, 1.0, 0.2, 1.0, 0.02);
        Fx.ring(server, position(), 2.5f, PURPLE, 10, 0);
        playSound(SoundEvents.WOLF_GROWL, 2f, 0.5f);
        double d = distanceTo(target);
        int next = !summonedWolves && !clone && getHealth() < getMaxHealth() * 0.5f ? HOWL
                : d > 5 ? POUNCE : random.nextBoolean() ? BITE : CLAW;
        startAttack(next);
    }

    /** A spot a few blocks from the target, preferring darkness (it steps out of shadows). */
    private Vec3 darkSpotNear(ServerLevel server, LivingEntity target) {
        Vec3 best = null;
        int bestLight = 99;
        for (int i = 0; i < 10; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double r = 3.5 + random.nextDouble() * 2.5;
            BlockPos pos = BlockPos.containing(target.getX() + Math.cos(a) * r, target.getY() + 2, target.getZ() + Math.sin(a) * r);
            for (int dy = 0; dy < 6 && server.getBlockState(pos.below()).isAir(); dy++) pos = pos.below();
            if (!server.getBlockState(pos).isAir()) continue;
            int light = server.getBrightness(LightLayer.BLOCK, pos);
            if (light < bestLight) {
                bestLight = light;
                best = Vec3.atBottomCenterOf(pos);
            }
        }
        return best != null ? best : target.position().add(target.getLookAngle().multiply(-3, 0, -3));
    }

    private void summonWolves() {
        summonedWolves = true;
        if (!(level() instanceof ServerLevel server)) return;
        for (int i = 0; i < 3; i++) {
            ShadowWolfEntity wolf = ModEntities.SHADOW_WOLF.get().create(server);
            if (wolf == null) continue;
            double a = Mth.TWO_PI * i / 3;
            wolf.moveTo(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3, getYRot(), 0);
            wolf.finalizeSpawn(server, server.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            wolf.setTarget(getTarget());
            server.addFreshEntity(wolf);
        }
    }

    @Override
    protected void customServerAiStep() {
        if (lifetime > 0 && --lifetime == 0) {
            particles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 40, 1.0, 0.02);
            discard();
            return;
        }
        if (isSubmerged()) {
            // While submerged it only drifts after its prey, out of sight.
            bossEvent.setProgress(getHealth() / getMaxHealth());
            LivingEntity target = getTarget();
            if (target != null && --submergedTicks <= 0) surface(target);
            if (target != null && tickCount % 4 == 0) {
                particles(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 2, 0.5, 0.0);
            }
            return;
        }
        super.customServerAiStep();
        if (attack < 0 && tickCount % 20 == 0) submerge();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && !isInvisible()) {
            for (int i = 0; i < 2; i++) {
                level().addParticle(AURA, getX() + (random.nextDouble() - 0.5) * getBbWidth() * 1.8,
                        getY() + random.nextDouble() * getBbHeight(), getZ() + (random.nextDouble() - 0.5) * getBbWidth() * 1.8, 0, 0.03, 0);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isSubmerged() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPickable() {
        return !isSubmerged() && super.isPickable();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!clone && level() instanceof ServerLevel server) Story.onLycagonShadowSlain(server, this, source.getEntity());
    }

    @Override
    protected boolean shouldDropLoot() {
        return !clone;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Clone", clone);
        tag.putInt("Lifetime", lifetime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        clone = tag.getBoolean("Clone");
        lifetime = tag.getInt("Lifetime");
        if (clone) bossEvent.setVisible(false);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isSubmerged() ? null : SoundEvents.WOLF_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.4f;
    }
}
