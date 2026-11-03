package com.slfrontier.entity.boss;

import com.slfrontier.entity.WolfVoice;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.entity.mob.ShadowWolfEntity;
import com.slfrontier.fx.Fx;
import com.slfrontier.quest.QuestStage;
import com.slfrontier.quest.Story;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
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
 * dark and cannot be harmed until it surfaces again (always somewhere in front of its prey). On
 * dark nights it spends less time sunk in the dark, but its blows give the same warning.
 *
 * <p>The same entity, in {@link #isClone() clone} mode, is the short-lived shadow that
 * the real Lycagon splits off in its second phase.
 */
public class LycagonShadowEntity extends FrontierBoss {
    public static final int BITE = 0, CLAW = 1, POUNCE = 2, HOWL = 3;
    private static final EntityDataAccessor<Boolean> SUBMERGED = SynchedEntityData.defineId(LycagonShadowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions AURA = new DustParticleOptions(0x8C33F2, 2.0f);
    private static final int PURPLE = 0xFFB070FF, PINK = 0xFFFF60E0;
    private static final float BITE_RANGE = 4.5f, BITE_ANGLE = 110, CLAW_RANGE = 5f, CLAW_ANGLE = 160;

    private boolean clone;
    private int submergedTicks = 40;
    private int lifetime = -1;
    private boolean summonedWolves;
    private Vec3 aim = Vec3.ZERO;
    /** Ticks it has stood surfaced with nothing to do. */
    private int surfacedIdle;

    public LycagonShadowEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, 45, 1800, BossEvent.BossBarColor.PURPLE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 520)
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

    /** Makes this a weaker, short-lived clone of the real Lycagon (worth no EXP and no loot). */
    public void makeClone(int lifetimeTicks) {
        clone = true;
        lifetime = lifetimeTicks;
        bossEvent.setVisible(false);
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(80);
        setHealth(80);
        submergedTicks = 10;
    }

    /** Sets the fight up for the player who woke the altar: a repeat fight if they already broke it. */
    public void configureFor(ServerPlayer player) {
        QuestStage stage = ModAttachments.get(player).stage();
        setRepeatKill(stage.ordinal() > QuestStage.LYCAGON_SHADOW.ordinal() || BossAltar.hasSlain(player, BossAltar.SHADOW));
    }

    /** A clone is a piece of Lycagon, not a prize: breaking it gives nothing. */
    @Override
    public int frontierExp() {
        return clone ? 0 : super.frontierExp();
    }

    /** Dark nights (and storms) make the shadow bolder: it spends less time sunk in the dark. */
    private boolean darkNight() {
        return level().isDarkOutside() && (level() instanceof ServerLevel server && server.getMoonBrightness(blockPosition()) < 0.5f
                || level().isRaining());
    }

    @Override
    protected int bossPhase() {
        return !clone && getHealth() < getMaxHealth() * 0.5f ? 2 : 1;
    }

    @Override
    protected String phaseHint(int phase) {
        return phase == 2 ? "message.slfrontier.phase.lycagon_shadow.2" : null;
    }

    /** Clones simply fade when their time is up. */
    @Override
    protected boolean canReset() {
        return !clone;
    }

    @Override
    protected void onReset() {
        summonedWolves = false;
        if (!isSubmerged()) submerge();
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
        // Every move gives the same warning on any night: dark nights only shorten its time in the dark.
        switch (attack) {
            case BITE -> {
                if (tick == 1) tellCone(yawToward(target), BITE_RANGE, BITE_ANGLE, true, 10);
                if (tick == 11) {
                    coneAttack(BITE_RANGE, BITE_ANGLE, 1.0f, 0.6, 0);
                    playSound(WolfVoice.GROWL, 1.5f, 0.6f);
                }
            }
            case CLAW -> {
                if (tick == 1) tellCone(yawToward(target), CLAW_RANGE, CLAW_ANGLE, true, 12);
                if (tick == 13) {
                    coneAttack(CLAW_RANGE, CLAW_ANGLE, 1.1f, 0.9, 0);
                    Fx.arc(this, 4.5f, 30, CLAW_ANGLE, PURPLE, 8);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
                }
            }
            case POUNCE -> {
                if (tick == 1) {
                    aim = target != null ? target.position() : position().add(facing().scale(4));
                    lockAim(Fx.yawTo(position(), aim));
                    tellCircle(aim, 3.2f, false, 12);
                }
                if (tick == 8) dashToward(aim, 1.2, 0.55);
                if (tick == 13) {
                    hitAt(aim, 3.2, 1.3f, 1.0, UNPARRYABLE);
                    Fx.ring(level(), aim, 3.5f, PINK, 10, 0.4f);
                }
            }
            case HOWL -> {
                if (tick == 10) {
                    playSound(WolfVoice.HOWL, 4f, 0.5f);
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
        surfacedIdle = 0;
        boolean dark = darkNight();
        submergedTicks = dark ? 35 + random.nextInt(25) : 50 + random.nextInt(30);
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
        surfacedIdle = 0;
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 40, 1.0, 0.2, 1.0, 0.02);
        Fx.ring(server, position(), 2.5f, PURPLE, 10, 0);
        playSound(WolfVoice.GROWL, 2f, 0.5f);
        double d = distanceTo(target);
        int next = !summonedWolves && !clone && getHealth() < getMaxHealth() * 0.5f ? HOWL
                : d > 5 ? POUNCE : random.nextBoolean() ? BITE : CLAW;
        startAttack(next);
    }

    /**
     * A spot a few blocks from the target, preferring darkness (it steps out of shadows), and always
     * somewhere in front of them: it never rises behind its prey.
     */
    private Vec3 darkSpotNear(ServerLevel server, LivingEntity target) {
        Vec3 look = target.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
        double facing = Math.atan2(look.z, look.x);
        Vec3 best = null;
        int bestLight = 99;
        for (int i = 0; i < 10; i++) {
            // Within 70 degrees either side of where the target is looking.
            double a = facing + (random.nextDouble() - 0.5) * Math.toRadians(140);
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
        return best != null ? best : target.position().add(look.scale(4));
    }

    private void summonWolves() {
        summonedWolves = true;
        if (!(level() instanceof ServerLevel server)) return;
        for (int i = 0; i < 3; i++) {
            ShadowWolfEntity wolf = ModEntities.SHADOW_WOLF.get().create(server, EntitySpawnReason.EVENT);
            if (wolf == null) continue;
            double a = Mth.TWO_PI * i / 3;
            wolf.snapTo(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3, getYRot(), 0);
            wolf.finalizeSpawn(server, server.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            wolf.setTarget(getTarget());
            server.addFreshEntity(wolf);
            trackSummon(wolf, true);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
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
        super.customServerAiStep(level);
        // Out of moves (it was parried, or its attack ended early): it lingers a moment, then sinks.
        if (attack < 0 && !isStunned() && !isRecovering() && ++surfacedIdle >= 15) submerge();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && !isInvisible()) {
            for (int i = 0; i < 2; i++) {
                level().addParticle(AURA, getX() + (random.nextDouble() - 0.5) * getBbWidth() * 1.8,
                        getY() + random.nextDouble() * getBbHeight(), getZ() + (random.nextDouble() - 0.5) * getBbWidth() * 1.8, 0, 0.03, 0);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isSubmerged() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            shrugOff(source);
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean isPickable() {
        return !isSubmerged() && super.isPickable();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!clone && level() instanceof ServerLevel server) {
            BossAltar.recordKill(server, this, source.getEntity(), BossAltar.SHADOW);
            Story.onLycagonShadowSlain(server, this, source.getEntity());
        }
    }

    @Override
    protected boolean shouldDropLoot(ServerLevel level) {
        return !clone;
    }

    @Override
    public void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Clone", clone);
        tag.putInt("Lifetime", lifetime);
    }

    @Override
    public void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
        clone = tag.getBooleanOr("Clone", false);
        lifetime = tag.getIntOr("Lifetime", 0);
        if (clone) bossEvent.setVisible(false);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isSubmerged() ? null : WolfVoice.GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WolfVoice.HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WolfVoice.DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.4f;
    }
}
