package com.slfrontier.entity;

import com.slfrontier.fx.Fx;
import com.slfrontier.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Base for bosses. Bosses fight with a simple state machine: chase the target, pick an
 * attack, play its animation as a telegraph and land the hit on a specific tick so
 * players can read and dodge (or parry) it.
 *
 * <p>Rules every boss follows:
 * <ul>
 *   <li>Telegraphs speak one colour language: a {@link #TELL_PARRY gold} zone (with a blade glint)
 *       can be parried, a {@link #TELL_DODGE crimson} one (with a warning tone) can only be dodged.
 *       Unparryable hits ({@link #UNPARRYABLE}) carry a damage source the parry check ignores.</li>
 *   <li>A blow lands exactly where its telegraph was drawn: the boss commits to the aim it showed
 *       ({@link #tellCone}, {@link #coneAttack}) and charges run their whole telegraphed length.</li>
 *   <li>A parry (or any stun) breaks off the current attack; some attacks end in a recovery window
 *       where the boss stands still and stops turning.</li>
 *   <li>With nobody to fight for a while it goes home, heals and forgets its phases and helpers.</li>
 * </ul>
 */
public abstract class FrontierBoss extends Monster implements LeveledMob, SlfAnimated {
    private static final double BOSS_BAR_RANGE = 48;
    /** Telegraph colour of a blow that can be parried (gold). */
    public static final int TELL_PARRY = 0xFFFFD45A;
    /** Telegraph colour of something that can only be dodged: eruptions, beams, magic (crimson). */
    public static final int TELL_DODGE = 0xFFFF2F4A;
    /** Hit flag: the parry can't stop this (eruptions, beams, lightning, missiles, auras). */
    protected static final int UNPARRYABLE = 1;
    /** Hit flag: a designed multi-hit; ignores the victim's hurt cooldown so every hit of the combo lands. */
    protected static final int COMBO = 2;
    /** Entity tag on helpers a boss called in (bees, howl wolves, shadow clones): they give no EXP. */
    public static final String SUMMON_TAG = "slfrontier.summon";
    /** With no enemy this close for {@link #LEASH_TICKS}, the boss goes home and recovers. */
    private static final double LEASH_RANGE = 40;
    private static final int LEASH_TICKS = 200;
    /** How long a target must stay out of reach before the boss uses its gap-closer. */
    private static final int UNREACHABLE_TICKS = 40;
    /** How long a boss reels after charging into a wall. */
    protected static final int WALL_STUN = 30;

    private final AnimationState[] anims = SlfAnimated.create();
    protected final ServerBossEvent bossEvent;
    private final int frontierLevel;
    private final int frontierExp;

    protected int attack = -1;
    protected int attackTick;
    protected int attackCooldown = 40;
    protected int idleTicks;
    /** Ticks left of a stun (a perfect parry, a wall): the boss holds still and its attack is broken off. */
    private int stunTicks;
    /** Highest phase announced so far (see {@link #bossPhase()}). Healing back doesn't replay it. */
    private int seenPhase = 1;
    /** Ticks left standing still after an attack (a punish window), and which attack it was. */
    private int recoveryTicks;
    private int recoveringFrom = -1;
    /** While locked, the boss faces {@link #lockedYaw} and its cone attacks use it (the aim it telegraphed). */
    private boolean aimLocked;
    private float lockedYaw;
    /** The last attack and how many times in a row it was used (no move three times running). */
    private int lastAttack = -1;
    private int repeats;
    /** A scripted follow-up (bite into claw...) to use as soon as the cooldown ends, or -1. */
    private int followUp = -1;
    private boolean reachable = true;
    private int unreachableTicks;

    @Nullable
    private Vec3 chargeDir;
    private Vec3 chargeFrom = Vec3.ZERO;
    private double chargeSpeed, chargeLength, chargeLift, chargeRadius, chargeKnockback;
    private float chargeScale;
    private int chargeFlags, chargeTicks;

    /** Where it fights from; it returns here to recover when nobody is left to fight. */
    @Nullable
    private Vec3 home;
    private int lonelyTicks;
    /** Something happened since the last reset (it was hurt or attacked), so a reset has work to do. */
    private boolean dirty;
    private final List<UUID> summons = new ArrayList<>();
    private int lastShrug = -100;

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

    /** Pick the next attack id, or -1 to keep chasing (it tries again a few ticks later). */
    protected abstract int chooseAttack(LivingEntity target, double distance);

    /** Total length of an attack in ticks (a charge in progress holds the clock until it ends). */
    protected abstract int attackLength(int attack);

    /** Called every tick of an attack, starting at tick 1. */
    protected abstract void tickAttack(int attack, int tick, LivingEntity target);

    /** Cooldown before choosing another attack. */
    protected int cooldownAfter(int attack) {
        return 20 + random.nextInt(20);
    }

    /** Ticks the boss stands still, not turning, after finishing {@code attack}: a window to punish it. */
    protected int recoveryAfter(int attack) {
        return 0;
    }

    /** Every tick of a recovery window (for effects: dizzy stars, steam, mud...). */
    protected void tickRecovery(int attack, int ticksLeft) {
    }

    /** Whether a stun (a parry) may break this attack off. Staggers and other scripted states say no. */
    protected boolean canInterrupt(int attack) {
        return true;
    }

    /** An attack ended, normally or broken off: undo whatever it changed (burrowed, invisible...). */
    protected void onAttackEnd(int attack, boolean interrupted) {
    }

    /**
     * The attack to use when the target has been out of reach (no path, or perched more than a
     * couple of blocks above) for a while, so pillaring up doesn't make anyone safe; -1 for none.
     */
    protected int gapCloser(LivingEntity target, double distance) {
        return -1;
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
        aimLocked = false;
        chargeDir = null;
        recoveryTicks = 0;
        dirty = true;
        if (id == lastAttack) {
            repeats++;
        } else {
            lastAttack = id;
            repeats = 1;
        }
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
                if (attack != id) break;  // it broke off or turned into another move
            }
            int was = attack;
            attack = -1;
            chargeDir = null;
            aimLocked = false;
            if (was >= 0) onAttackEnd(was, false);
        }
        attack = -1;
    }

    public boolean isAttacking() {
        return attack >= 0;
    }

    public int currentAttack() {
        return attack;
    }

    /** True if using {@code id} now would make it the same move three times in a row. */
    protected boolean wouldRepeat(int id) {
        return id >= 0 && id == lastAttack && repeats >= 2;
    }

    /**
     * Picks one of the (attack id, weight) pairs at random by weight. An id of -1 means "keep closing
     * in". Moves that would be used a third time in a row, and weights of 0 or less, are skipped.
     */
    protected int pick(int... idWeight) {
        int total = 0;
        for (int i = 0; i + 1 < idWeight.length; i += 2) {
            if (idWeight[i + 1] > 0 && !wouldRepeat(idWeight[i])) total += idWeight[i + 1];
        }
        if (total <= 0) return -1;
        int r = random.nextInt(total);
        for (int i = 0; i + 1 < idWeight.length; i += 2) {
            if (idWeight[i + 1] <= 0 || wouldRepeat(idWeight[i])) continue;
            r -= idWeight[i + 1];
            if (r < 0) return idWeight[i];
        }
        return -1;
    }

    /** Queues a scripted follow-up (a string such as bite, claw, backstep) to start after {@code delay} ticks. */
    protected void followWith(int id, int delay) {
        followUp = id;
        attackCooldown = delay;
    }

    /** Knocked off balance: holds still, and whatever it was doing is broken off. */
    public void stun(int ticks) {
        stunTicks = Math.max(stunTicks, ticks);
        recoveryTicks = 0;
        getNavigation().stop();
        if (attack >= 0 && canInterrupt(attack)) {
            cancelAttack(ticks + 15);
        } else if (attack < 0) {
            attackCooldown = Math.max(attackCooldown, ticks + 15);
            followUp = -1;
        }
    }

    public boolean isStunned() {
        return stunTicks > 0;
    }

    /** A player perfectly parried one of its blows: it reels and the attack is broken off. */
    public void onParried(ServerPlayer by) {
        Stun.apply(this, Stun.PARRY_TICKS);
    }

    /** Breaks the current attack off, with at least {@code cooldown} ticks before the next one. */
    protected void cancelAttack(int cooldown) {
        if (attack < 0) return;
        int was = attack;
        attack = -1;
        aimLocked = false;
        chargeDir = null;
        followUp = -1;
        attackCooldown = Math.max(attackCooldown, cooldown);
        onAttackEnd(was, true);
    }

    private void finishAttack() {
        int done = attack;
        attack = -1;
        chargeDir = null;
        if (followUp < 0) attackCooldown = cooldownAfter(done);
        int recovery = recoveryAfter(done);
        if (recovery > 0) {
            recoveryTicks = recovery;
            recoveringFrom = done;
            lockAim(getYRot());
        } else {
            aimLocked = false;
        }
        onAttackEnd(done, false);
    }

    public boolean isRecovering() {
        return recoveryTicks > 0;
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
        if (chargeDir != null && attack >= 0) {
            tickCharge();
            return;
        }
        if (attack >= 0) {
            if (aimLocked) {
                faceLocked();
            } else if (target != null) {
                getLookControl().setLookAt(target, 30f, 30f);
            }
            attackTick++;
            int running = attack;
            tickAttack(running, attackTick, target);
            if (attack == running && chargeDir == null && attackTick >= attackLength(running)) finishAttack();
            return;
        }
        if (recoveryTicks > 0) {
            recoveryTicks--;
            getNavigation().stop();
            faceLocked();
            tickRecovery(recoveringFrom, recoveryTicks);
            if (recoveryTicks == 0) aimLocked = false;
            return;
        }
        aimLocked = false;
        if (target == null) {
            idleTicks++;
            return;
        }
        idleTicks = 0;
        getLookControl().setLookAt(target, 30f, 30f);
        double distance = distanceTo(target);
        if (tickCount % 10 == 0 || (getNavigation().isDone() && tickCount % 4 == 0)) {
            boolean moving = getNavigation().moveTo(target, chaseSpeed());
            Path path = getNavigation().getPath();
            reachable = !moving || path == null ? distance < 3 : path.canReach();
        }
        boolean perched = target.getY() - getY() > 2.5;
        unreachableTicks = !reachable || perched ? unreachableTicks + 1 : 0;
        if (--attackCooldown <= 0) {
            int next = -1;
            if (unreachableTicks >= UNREACHABLE_TICKS) {
                next = gapCloser(target, distance);
                if (next >= 0) unreachableTicks = 0;
            }
            if (next < 0 && followUp >= 0) next = followUp;
            followUp = -1;
            if (next < 0) next = chooseAttack(target, distance);
            if (next >= 0) {
                startAttack(next);
            } else {
                attackCooldown = 6;
            }
        }
    }

    // ---- Committed aim and charges ------------------------------------------------

    /** Faces {@code yaw} and stops turning until the attack ends: cone attacks land along this aim. */
    protected void lockAim(float yaw) {
        aimLocked = true;
        lockedYaw = yaw;
        faceLocked();
    }

    private void faceLocked() {
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        setYHeadRot(lockedYaw);
        Vec3 ahead = position().add(Vec3.directionFromRotation(0, lockedYaw).scale(4)).add(0, getEyeHeight(), 0);
        getLookControl().setLookAt(ahead.x, ahead.y, ahead.z, 30f, 30f);
    }

    /** Yaw from the boss toward {@code target} (or its current facing without one). */
    protected float yawToward(@Nullable LivingEntity target) {
        return target != null ? Fx.yawTo(position(), target.position()) : getYRot();
    }

    /** Horizontal unit vector of the boss's aim: the committed one while locked. */
    protected Vec3 facing() {
        return Vec3.directionFromRotation(0, aimLocked ? lockedYaw : getYRot());
    }

    /**
     * A committed charge: it runs along {@code dir} at {@code speed} blocks a tick until it covered
     * {@code length} (the telegraph), hit someone, or ran into a wall, which leaves it reeling for
     * {@link #WALL_STUN} ticks. The attack's own clock waits while it runs.
     */
    protected void startCharge(Vec3 dir, double speed, double length, double lift, double radius, float damageScale,
                               double knockback, int flags) {
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1e-6) return;
        chargeDir = flat.normalize();
        chargeFrom = position();
        chargeSpeed = speed;
        chargeLength = length;
        chargeLift = lift;
        chargeRadius = radius;
        chargeScale = damageScale;
        chargeKnockback = knockback;
        chargeFlags = flags;
        chargeTicks = 0;
        lockAim(Fx.yawTo(Vec3.ZERO, chargeDir));
        getNavigation().stop();
    }

    public boolean isCharging() {
        return chargeDir != null;
    }

    private void tickCharge() {
        Vec3 dir = chargeDir;
        if (dir == null) return;
        chargeTicks++;
        if (chargeTicks > 1 && horizontalCollision) {
            endCharge(true);
            return;
        }
        faceLocked();
        setDeltaMovement(dir.x * chargeSpeed, chargeTicks == 1 && chargeLift != 0 ? chargeLift : getDeltaMovement().y, dir.z * chargeSpeed);
        needsSync = true;
        int hits = hitAround(chargeRadius, chargeScale, -1, chargeKnockback, chargeFlags);
        if (chargeDir == null) return;  // parried mid-charge
        double dx = getX() - chargeFrom.x, dz = getZ() - chargeFrom.z;
        double left = chargeLength - Math.sqrt(dx * dx + dz * dz);
        if (hits > 0 || left <= chargeSpeed * 0.5 || chargeTicks > chargeLength / Math.max(0.1, chargeSpeed) + 8) {
            endCharge(false);
        }
    }

    private void endCharge(boolean wall) {
        chargeDir = null;
        Vec3 m = getDeltaMovement();
        setDeltaMovement(m.x * 0.2, m.y, m.z * 0.2);
        needsSync = true;
        if (wall && level() instanceof ServerLevel server) {
            Vec3 front = position().add(facing().scale(getBbWidth() * 0.6)).add(0, getBbHeight() * 0.5, 0);
            Fx.flash(server, front, Math.max(1.5f, getBbWidth()), 0xFFFFFFFF, 6, 0.5f);
            server.sendParticles(ParticleTypes.CRIT, front.x, front.y, front.z, 16, 0.4, 0.4, 0.4, 0.2);
            playSound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 2f, 0.5f);
            Stun.apply(this, WALL_STUN);
        }
    }

    // ---- Telegraphs ----------------------------------------------------------------

    protected static int tellColor(boolean parryable) {
        return parryable ? TELL_PARRY : TELL_DODGE;
    }

    /** The wind-up sound: a blade glint for something parryable, a warning tone for something to dodge. */
    protected void tellSound(boolean parryable) {
        level().playSound(null, getX(), getY(), getZ(), parryable ? ModSounds.BOSS_PARRY_GLINT : ModSounds.BOSS_TELEGRAPH,
                getSoundSource(), 1.6f, 1f);
    }

    /** Draws a cone from the boss toward {@code yaw} and commits to it (see {@link #coneAttack}). */
    protected void tellCone(float yaw, float radius, float angle, boolean parryable, int duration) {
        lockAim(yaw);
        Fx.coneAt(level(), position(), yaw, radius, angle, tellColor(parryable), duration);
        tellSound(parryable);
    }

    /** A danger circle at {@code at}. */
    protected void tellCircle(Vec3 at, float radius, boolean parryable, int duration) {
        Fx.telegraph(level(), at, radius, tellColor(parryable), duration);
        tellSound(parryable);
    }

    // ---- Phases and feedback --------------------------------------------------------

    /** 1 for bosses without phases; phased bosses override this (Wezaemon 1-3, Lycagon 1-2...). */
    protected int bossPhase() {
        return 1;
    }

    /** Translation key of the short line explaining what changes in {@code phase}, or null. */
    @Nullable
    protected String phaseHint(int phase) {
        return null;
    }

    /** A new phase begins: everyone near hears it, sees the boss flare and reads what changed. */
    protected void onPhaseChange(ServerLevel level, int phase) {
        level.playSound(null, getX(), getY(), getZ(), ModSounds.BOSS_PHASE, getSoundSource(), 2.5f, 1f);
        Fx.flash(level, position().add(0, getBbHeight() * 0.6, 0), Math.max(2f, getBbWidth() * 1.5f), 0xFFFFE2A0, 10, 0.8f);
        String hint = phaseHint(phase);
        if (hint != null) {
            for (ServerPlayer p : playersNear(32)) {
                p.sendOverlayMessage(Component.translatable(hint).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
        }
    }

    /** A blow that can't touch it right now (burrowed, submerged): a dull thud and a puff, so the player knows. */
    protected void shrugOff(DamageSource source) {
        if (!(source.getEntity() instanceof Player) || tickCount - lastShrug < 8 || !(level() instanceof ServerLevel server)) return;
        lastShrug = tickCount;
        Vec3 at = source.getSourcePosition() != null ? source.getSourcePosition() : position().add(0, getBbHeight() * 0.4, 0);
        server.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.3, at.z, 10, 0.3, 0.3, 0.3, 0.02);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, getSoundSource(), 0.9f, 0.5f);
    }

    // ---- Home, reset and summons -----------------------------------------------------

    /** Whether the generic leash may reset this boss now (clones and mounts follow other rules). */
    protected boolean canReset() {
        return true;
    }

    /** Called by a reset, after it healed: put back phase flags, cooldowns and the like. */
    protected void onReset() {
    }

    @Nullable
    protected Vec3 home() {
        return home;
    }

    private void tickLeash() {
        if (!canReset() || isDeadOrDying() || engaged()) {
            lonelyTicks = 0;
            return;
        }
        lonelyTicks += 20;
        if (lonelyTicks >= LEASH_TICKS && attack < 0 && stunTicks <= 0) {
            lonelyTicks = 0;
            resetFight();
        }
    }

    private boolean engaged() {
        LivingEntity target = getTarget();
        if (target != null && target.isAlive() && target.distanceToSqr(this) < LEASH_RANGE * LEASH_RANGE
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            return true;
        }
        return !level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(LEASH_RANGE),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(this) < LEASH_RANGE * LEASH_RANGE).isEmpty();
    }

    /** Nobody is left to fight: go home, heal fully, forget phases, send helpers away. */
    protected void resetFight() {
        if (!dirty || !(level() instanceof ServerLevel server)) return;
        dirty = false;
        stunTicks = 0;
        recoveryTicks = 0;
        aimLocked = false;
        chargeDir = null;
        followUp = -1;
        unreachableTicks = 0;
        lastAttack = -1;
        repeats = 0;
        setTarget(null);
        getNavigation().stop();
        despawnSummons(true);
        if (home != null && distanceToSqr(home) > 9) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 0.8, 0.8, 0.8, 0.02);
            teleportTo(home.x, home.y, home.z);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 0.8, 0.8, 0.8, 0.02);
        }
        removeAllEffects();
        setHealth(getMaxHealth());
        seenPhase = 1;
        attackCooldown = 40;
        bossEvent.setProgress(1f);
        onReset();
    }

    /** Remembers a helper this boss called in, so a reset (or its death) sends it away. */
    protected void trackSummon(Entity helper, boolean noReward) {
        summons.add(helper.getUUID());
        if (noReward) helper.addTag(SUMMON_TAG);
    }

    /** A helper some boss called in (bees, howl wolves, shadow clones): worth no EXP. */
    public static boolean isSummon(Entity entity) {
        return entity.entityTags().contains(SUMMON_TAG);
    }

    /** Sends helpers away: all of them, or only the ones that give nothing (mounts such as Kirin leave on their own). */
    protected void despawnSummons(boolean all) {
        if (!(level() instanceof ServerLevel server)) return;
        for (Iterator<UUID> it = summons.iterator(); it.hasNext(); ) {
            Entity e = server.getEntity(it.next());
            if (e == null || !e.isAlive()) {
                it.remove();
                continue;
            }
            if (!all && !isSummon(e)) continue;
            server.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
            e.discard();
            it.remove();
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
            return;
        }
        if (tickCount % 10 == 0) updateBossBarViewers();
        if (home == null) home = position();
        if (tickCount % 20 == 0 && isAlive()) tickLeash();
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

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), ModSounds.BOSS_DEFEATED, getSoundSource(), 3f, 1f);
            // Its swarm scatters with it (a mount such as Kirin falls apart on its own).
            despawnSummons(false);
        }
        bossEvent.setProgress(0f);
        bossEvent.removeAllPlayers();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) dirty = true;
        return hurt;
    }

    // ---- Hits ------------------------------------------------------------------------

    /** A blow the parry check (CommonEvents) ignores: the same damage type, just unparryable. */
    public static final class UnparryableSource extends DamageSource {
        UnparryableSource(DamageSource base) {
            super(base.typeHolder(), base.getDirectEntity(), base.getEntity());
        }
    }

    /** Whether the parry can't stop this damage (eruptions, beams, magic, auras). */
    public static boolean isUnparryable(DamageSource source) {
        return source instanceof UnparryableSource;
    }

    protected DamageSource hitSource(int flags) {
        DamageSource base = damageSources().mobAttack(this);
        return (flags & UNPARRYABLE) != 0 ? new UnparryableSource(base) : base;
    }

    /** Wraps any damage source so the parry can't stop it (for auras and other non-blow damage). */
    protected static DamageSource unparryable(DamageSource source) {
        return source instanceof UnparryableSource ? source : new UnparryableSource(source);
    }

    protected float attackDamage() {
        return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /**
     * Hits everyone inside a cone in front of the boss: {@code angleDeg} is the full angle, the same
     * number its {@link #tellCone telegraph} was drawn with, so what you see is what gets hit.
     */
    protected int coneAttack(double radius, float angleDeg, float damageScale, double knockback, int flags) {
        double minDot = angleDeg >= 360 ? -1 : Math.cos(Math.toRadians(angleDeg / 2));
        return hitAround(radius, damageScale, minDot, knockback, flags);
    }

    /** Damages every valid target within {@code radius}, optionally only in front (dot > minDot). */
    protected int hitAround(double radius, float damageScale, double minDot, double knockback) {
        return hitAround(radius, damageScale, minDot, knockback, 0);
    }

    /** As {@link #hitAround(double, float, double, double)}, with hit flags ({@link #UNPARRYABLE}, {@link #COMBO}). */
    protected int hitAround(double radius, float damageScale, double minDot, double knockback, int flags) {
        Vec3 look = facing();
        double half = minDot <= -1 ? Math.PI : Math.acos(Math.max(-1, Math.min(1, minDot)));
        int hits = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 2, radius),
                e -> e != this && e.isAlive() && isEnemy(e))) {
            Vec3 to = e.position().subtract(position());
            double dist = Math.sqrt(to.x * to.x + to.z * to.z);
            if (dist > radius + e.getBbWidth() / 2) continue;
            if (minDot > -1 && dist > 0.5) {
                // Anyone with part of their body inside the drawn cone is in it.
                double cos = Math.max(-1, Math.min(1, (to.x * look.x + to.z * look.z) / dist));
                double slack = Math.asin(Math.min(1, e.getBbWidth() / 2 / dist));
                if (Math.acos(cos) > half + slack) continue;
            }
            if (strike(e, damageScale, to.x, to.z, knockback, flags)) hits++;
        }
        return hits;
    }

    /** Damages every valid target within {@code radius} of a point. */
    protected int hitAt(Vec3 at, double radius, float damageScale, double knockback) {
        return hitAt(at, radius, damageScale, knockback, 0);
    }

    protected int hitAt(Vec3 at, double radius, float damageScale, double knockback, int flags) {
        int hits = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, radius + 1, radius),
                e -> e != this && e.isAlive() && isEnemy(e))) {
            Vec3 to = e.position().subtract(at);
            if (Math.sqrt(to.x * to.x + to.z * to.z) > radius + e.getBbWidth() / 2) continue;
            if (strike(e, damageScale, to.x, to.z, knockback, flags)) hits++;
        }
        return hits;
    }

    /** Damages every valid target within {@code width} of the segment {@code from}-{@code to} (beams, wind blades). */
    protected int hitLine(Vec3 from, Vec3 to, double width, float damageScale, double knockback) {
        return hitLine(from, to, width, damageScale, knockback, 0);
    }

    protected int hitLine(Vec3 from, Vec3 to, double width, float damageScale, double knockback, int flags) {
        Vec3 d = to.subtract(from);
        double len2 = Math.max(1e-6, d.lengthSqr());
        int hits = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(width + 1),
                e -> e != this && e.isAlive() && isEnemy(e))) {
            Vec3 p = e.position().add(0, e.getBbHeight() / 2, 0);
            double t = Math.max(0, Math.min(1, p.subtract(from).dot(d) / len2));
            Vec3 closest = from.add(d.scale(t));
            if (closest.distanceTo(p) > width + e.getBbWidth() / 2) continue;
            if (strike(e, damageScale, d.x, d.z, knockback, flags)) hits++;
        }
        return hits;
    }

    /** Players (and the current target) worth aiming a telegraphed attack at. */
    protected List<LivingEntity> enemiesNear(double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 6, radius),
                e -> e != this && e.isAlive() && isEnemy(e) && e.distanceTo(this) <= radius);
    }

    private boolean strike(LivingEntity e, float damageScale, double dx, double dz, double knockback, int flags) {
        float dealt = adjustDamage(e, attackDamage() * damageScale);
        if (dealt <= 0) return false;
        // A designed combo: the victim's hurt cooldown from the previous hit mustn't swallow the next one.
        if ((flags & COMBO) != 0 && e.invulnerableTime > 10) e.invulnerableTime = 0;
        if (!e.hurtOrSimulate(hitSource(flags), dealt)) return false;
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

    /** One push toward {@code target} (short hops; committed charges use {@link #startCharge}). */
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
        home = tag.read("SlfHome", Vec3.CODEC).orElse(null);
        dirty = tag.getBooleanOr("SlfDirty", true);
        summons.clear();
        tag.read("SlfSummons", UUIDUtil.CODEC.listOf()).ifPresent(summons::addAll);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SeenPhase", seenPhase);
        if (home != null) tag.store("SlfHome", Vec3.CODEC, home);
        tag.putBoolean("SlfDirty", dirty);
        if (!summons.isEmpty()) tag.store("SlfSummons", UUIDUtil.CODEC.listOf(), List.copyOf(summons));
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
