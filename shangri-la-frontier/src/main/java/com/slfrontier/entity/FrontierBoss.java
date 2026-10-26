package com.slfrontier.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Base for bosses. Bosses fight with a simple state machine: chase the target, pick an
 * attack, play its animation as a telegraph and land the hit on a specific tick so
 * players can read and dodge (or parry) it.
 */
public abstract class FrontierBoss extends Monster implements LeveledMob, SlfAnimated {
    private final AnimationState[] anims = SlfAnimated.create();
    protected final ServerBossEvent bossEvent;
    private final int frontierLevel;
    private final int frontierExp;

    protected int attack = -1;
    protected int attackTick;
    protected int attackCooldown = 40;
    protected int idleTicks;

    protected FrontierBoss(EntityType<? extends Monster> type, Level level, int frontierLevel, int frontierExp,
                           BossEvent.BossBarColor color) {
        super(type, level);
        this.frontierLevel = frontierLevel;
        this.frontierExp = frontierExp;
        this.xpReward = 100;
        this.bossEvent = new ServerBossEvent(getDisplayName(), color, BossEvent.BossBarOverlay.NOTCHED_10);
        this.bossEvent.setDarkenScreen(true);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ---- Attack state machine ----------------------------------------------------

    /** Pick the next attack id, or -1 to keep chasing. */
    protected abstract int chooseAttack(LivingEntity target, double distance);

    /** Total length of an attack in ticks. */
    protected abstract int attackLength(int attack);

    /** Called every tick of an attack, starting at tick 1. */
    protected abstract void tickAttack(int attack, int tick, LivingEntity target);

    /** Cooldown before choosing another attack. */
    protected int cooldownAfter(int attack) {
        return 20 + random.nextInt(20);
    }

    protected double chaseSpeed() {
        return 1.0;
    }

    /** Animation slot to play for an attack (slot 0 is idle). */
    protected int animSlot(int attack) {
        return attack + 1;
    }

    protected void startAttack(int id) {
        attack = id;
        attackTick = 0;
        getNavigation().stop();
        playAnim(animSlot(id));
    }

    public boolean isAttacking() {
        return attack >= 0;
    }

    public int currentAttack() {
        return attack;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || (target instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            setTarget(null);
            target = null;
        }
        if (attack >= 0) {
            attackTick++;
            if (target != null) getLookControl().setLookAt(target, 30f, 30f);
            tickAttack(attack, attackTick, target);
            if (attackTick >= attackLength(attack)) {
                attackCooldown = cooldownAfter(attack);
                attack = -1;
            }
            return;
        }
        if (target == null) {
            idleTicks++;
            return;
        }
        idleTicks = 0;
        getLookControl().setLookAt(target, 30f, 30f);
        double distance = distanceTo(target);
        if (tickCount % 10 == 0 || getNavigation().isDone()) {
            getNavigation().moveTo(target, chaseSpeed());
        }
        if (--attackCooldown <= 0) {
            int next = chooseAttack(target, distance);
            if (next >= 0) startAttack(next);
        }
    }

    // ---- Helpers for subclasses ----------------------------------------------------

    public void playAnim(int slot) {
        if (!level().isClientSide) {
            level().broadcastEntityEvent(this, (byte) (ANIM_EVENT_BASE + slot));
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= ANIM_EVENT_BASE && id < ANIM_EVENT_BASE + MAX_ANIMS) {
            startAnim(id - ANIM_EVENT_BASE, tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            anims[0].startIfStopped(tickCount);
        }
    }

    /** Damages every valid target within {@code radius}, optionally only in front (dot > minDot). */
    protected int hitAround(double radius, float damageScale, double minDot, double knockback) {
        float damage = (float) getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * damageScale;
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        int hits = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 2, radius),
                e -> e != this && e.isAlive() && isEnemy(e))) {
            Vec3 to = e.position().subtract(position());
            double dist = Math.sqrt(to.x * to.x + to.z * to.z);
            if (dist > radius + e.getBbWidth() / 2) continue;
            if (minDot > -1 && dist > 0.5 && (to.x * look.x + to.z * look.z) / dist < minDot) continue;
            if (e.hurt(damageSources().mobAttack(this), damage)) {
                hits++;
                if (knockback > 0) e.knockback(knockback, -to.x, -to.z);
                onHitTarget(e);
            }
        }
        return hits;
    }

    protected boolean isEnemy(LivingEntity e) {
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e == getTarget();
    }

    /** Hook for effects such as Lycagon's mark. */
    protected void onHitTarget(LivingEntity target) {
    }

    protected void particles(ParticleOptions particle, double x, double y, double z, int count, double spread, double speed) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(particle, x, y, z, count, spread, spread * 0.5, spread, speed);
        }
    }

    protected void ring(ParticleOptions particle, double radius, int count, double height) {
        if (level() instanceof ServerLevel server) {
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2 * i / count;
                server.sendParticles(particle, getX() + Math.cos(a) * radius, getY() + height, getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
            }
        }
    }

    protected List<ServerPlayer> playersNear(double radius) {
        return level().getEntitiesOfClass(ServerPlayer.class, new AABB(blockPosition()).inflate(radius),
                p -> !p.isSpectator());
    }

    protected void dashToward(Vec3 target, double speed, double lift) {
        Vec3 d = target.subtract(position());
        Vec3 dir = new Vec3(d.x, 0, d.z).normalize();
        setDeltaMovement(dir.x * speed, lift, dir.z * speed);
        hasImpulse = true;
    }

    // ---- Boss boilerplate ----------------------------------------------------------

    @Override
    public int frontierLevel() {
        return frontierLevel;
    }

    @Override
    public int frontierExp() {
        return frontierExp;
    }

    @Override
    public AnimationState[] animStates() {
        return anims;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING)
                || super.isInvulnerableTo(source);
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return super.doHurtTarget(target);
    }
}
