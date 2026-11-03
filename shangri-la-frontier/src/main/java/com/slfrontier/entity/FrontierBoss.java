package com.slfrontier.entity;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Base for bosses. Bosses fight with a simple state machine: chase the target, pick an
 * attack, play its animation as a telegraph and land the hit on a specific tick so
 * players can read and dodge (or parry) it.
 */
public abstract class FrontierBoss extends Monster implements LeveledMob, SlfAnimated {
    private static final double BOSS_BAR_RANGE = 48;
    private final AnimationState[] anims = SlfAnimated.create();
    protected final ServerBossEvent bossEvent;
    private final int frontierLevel;
    private final int frontierExp;

    protected int attack = -1;
    protected int attackTick;
    protected int attackCooldown = 40;
    protected int idleTicks;
    /** Ticks left of a stun (a perfect parry): the boss holds still and its attack pauses. */
    private int stunTicks;
    /** Highest phase announced so far (see {@link #bossPhase()}). Healing back doesn't replay it. */
    private int seenPhase = 1;

    /** Bosses whose bar someone can see, so {@link #sweepBossBars} can clear bars of bosses that stopped ticking. */
    private static final Set<FrontierBoss> SHOWING = Collections.newSetFromMap(new WeakHashMap<>());
    private int sweptAt = -1;

    protected FrontierBoss(EntityType<? extends Monster> type, Level level, int frontierLevel, int frontierExp,
                           BossEvent.BossBarColor color) {
        super(type, level);
        this.frontierLevel = frontierLevel;
        this.frontierExp = frontierExp;
        this.xpReward = 100;
        this.bossEvent = new ServerBossEvent(getUUID(), getDisplayName(), color, BossEvent.BossBarOverlay.NOTCHED_10);
        this.bossEvent.setDarkenScreen(true);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        // Allied NPCs (Pencilgon, Oikatzo, Emul) chip in, but the boss keeps its eyes on the players.
        targetSelector.addGoal(1, new HurtByTargetGoal(this, com.slfrontier.entity.npc.FrontierNpc.class));
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

    /** Starts a specific attack right away (used by the client test scenes to show telegraphs). */
    public void forceAttack(int id) {
        startAttack(id);
    }

    /** Runs every attack's whole tick sequence once against {@code target} (used by the story self-test). */
    public void exerciseAttacks(LivingEntity target, int maxId) {
        for (int id = 0; id <= maxId; id++) {
            attack = id;
            int length = attackLength(id);
            for (int t = 1; t <= length && isAlive() && !isRemoved(); t++) {
                attackTick = t;
                tickAttack(id, t, target);
            }
        }
        attack = -1;
    }

    public boolean isAttacking() {
        return attack >= 0;
    }

    public int currentAttack() {
        return attack;
    }

    /** Knocked off balance: holds still and pauses whatever it was doing (attacks resume afterwards). */
    public void stun(int ticks) {
        stunTicks = Math.max(stunTicks, ticks);
        getNavigation().stop();
    }

    public boolean isStunned() {
        return stunTicks > 0;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        bossEvent.setProgress(getHealth() / getMaxHealth());
        int phase = bossPhase();
        if (phase > seenPhase) {
            seenPhase = phase;
            onPhaseChange(level, phase);
        }
        if (stunTicks > 0) {
            stunTicks--;
            getNavigation().stop();
            return;
        }
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
        if (!level().isClientSide()) {
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
        if (level().isClientSide()) {
            anims[0].startIfStopped(tickCount);
        } else if (tickCount % 10 == 0) {
            updateBossBarViewers();
        }
    }

    /** Only players actually near the fight see the boss bar, and nobody sees a fallen boss's bar. */
    private void updateBossBarViewers() {
        if (isDeadOrDying()) {
            bossEvent.removeAllPlayers();
            return;
        }
        dropFarViewers();
        for (ServerPlayer p : playersNear(BOSS_BAR_RANGE * 0.8)) {
            bossEvent.addPlayer(p);
            SHOWING.add(this);
        }
    }

    private void dropFarViewers() {
        for (ServerPlayer p : new ArrayList<>(bossEvent.getPlayers())) {
            if (p.isRemoved() || !p.isAlive() || p.level() != level() || p.distanceToSqr(this) > BOSS_BAR_RANGE * BOSS_BAR_RANGE) {
                bossEvent.removePlayer(p);
            }
        }
    }

    /**
     * Runs every 10 server ticks. A boss only updates its own bar while it ticks, so a boss whose
     * chunk fell out of simulation range (you fast-traveled, died and respawned elsewhere, or ran)
     * would leave its bar on screen; this clears those, and viewers who left or respawned.
     */
    public static void sweepBossBars() {
        for (Iterator<FrontierBoss> it = SHOWING.iterator(); it.hasNext(); ) {
            FrontierBoss boss = it.next();
            boolean ticking = boss.tickCount != boss.sweptAt;
            boss.sweptAt = boss.tickCount;
            if (boss.isRemoved() || boss.isDeadOrDying() || !ticking) {
                boss.bossEvent.removeAllPlayers();
                it.remove();
            } else {
                boss.dropFarViewers();
                if (boss.bossEvent.getPlayers().isEmpty()) it.remove();
            }
        }
    }

    /** 1 for bosses without phases; phased bosses override this (Wezaemon 1-3, Lycagon 1-2). */
    protected int bossPhase() {
        return 1;
    }

    /** A new phase begins: everyone near hears it and sees the boss flare. */
    protected void onPhaseChange(ServerLevel level, int phase) {
        level.playSound(null, getX(), getY(), getZ(), com.slfrontier.registry.ModSounds.BOSS_PHASE, getSoundSource(), 2.5f, 1f);
        com.slfrontier.fx.Fx.flash(level, position().add(0, getBbHeight() * 0.6, 0), Math.max(2f, getBbWidth() * 1.5f), 0xFFFFE2A0, 10, 0.8f);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), com.slfrontier.registry.ModSounds.BOSS_DEFEATED, getSoundSource(), 3f, 1f);
        }
        bossEvent.setProgress(0f);
        bossEvent.removeAllPlayers();
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
            float dealt = adjustDamage(e, damage);
            if (dealt <= 0) continue;
            if (e.hurtOrSimulate(damageSources().mobAttack(this), dealt)) {
                hits++;
                if (knockback > 0) e.knockback(knockback, -to.x, -to.z, e.damageSources().generic(), 0f);
                onHitTarget(e);
            }
        }
        return hits;
    }

    /** Damages every valid target within {@code radius} of a point. */
    protected int hitAt(Vec3 at, double radius, float damageScale, double knockback) {
        int hits = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, radius + 1, radius),
                e -> e != this && e.isAlive() && isEnemy(e))) {
            Vec3 to = e.position().subtract(at);
            if (Math.sqrt(to.x * to.x + to.z * to.z) > radius + e.getBbWidth() / 2) continue;
            if (strike(e, damageScale, to.x, to.z, knockback)) hits++;
        }
        return hits;
    }

    /** Damages every valid target within {@code width} of the segment {@code from}-{@code to} (beams, wind blades). */
    protected int hitLine(Vec3 from, Vec3 to, double width, float damageScale, double knockback) {
        Vec3 d = to.subtract(from);
        double len2 = Math.max(1e-6, d.lengthSqr());
        int hits = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(width + 1),
                e -> e != this && e.isAlive() && isEnemy(e))) {
            Vec3 p = e.position().add(0, e.getBbHeight() / 2, 0);
            double t = Math.max(0, Math.min(1, p.subtract(from).dot(d) / len2));
            Vec3 closest = from.add(d.scale(t));
            if (closest.distanceTo(p) > width + e.getBbWidth() / 2) continue;
            if (strike(e, damageScale, d.x, d.z, knockback)) hits++;
        }
        return hits;
    }

    /** Players (and the current target) worth aiming a telegraphed attack at. */
    protected List<LivingEntity> enemiesNear(double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 6, radius),
                e -> e != this && e.isAlive() && isEnemy(e) && e.distanceTo(this) <= radius);
    }

    private boolean strike(LivingEntity e, float damageScale, double dx, double dz, double knockback) {
        float damage = (float) getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * damageScale;
        float dealt = adjustDamage(e, damage);
        if (dealt <= 0 || !e.hurtOrSimulate(damageSources().mobAttack(this), dealt)) return false;
        if (knockback > 0 && (dx != 0 || dz != 0)) e.knockback(knockback, -dx, -dz, e.damageSources().generic(), 0f);
        onHitTarget(e);
        return true;
    }

    protected boolean isEnemy(LivingEntity e) {
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e == getTarget();
    }

    /** Lets a boss change (or cancel, by returning 0) the damage of one of its hits. */
    protected float adjustDamage(LivingEntity target, float damage) {
        return damage;
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
        needsSync = true;
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
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        // Viewers are added by distance, not tracking, so drop them all when the boss goes away.
        bossEvent.removeAllPlayers();
    }

    /** Also called when the boss's chunk unloads, which doesn't go through {@link #remove}. */
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        bossEvent.removeAllPlayers();
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
        if (hasCustomName()) bossEvent.setName(getDisplayName());
        seenPhase = Math.max(1, tag.getIntOr("SeenPhase", bossPhase()));
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SeenPhase", seenPhase);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING)
                || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean canTeleport(Level from, Level to) {
        return false;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        return super.doHurtTarget(level, target);
    }
}
