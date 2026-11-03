package com.slfrontier.entity.mob;

import com.slfrontier.Sfx;
import com.slfrontier.entity.FrontierMonster;
import com.slfrontier.entity.Stun;
import com.slfrontier.fx.Fx;
import com.slfrontier.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Melee with a tell, for every regular monster: it stops, a small danger cone fills up on the
 * ground in front of it while it winds up, its attack animation lands at the end, and the blow
 * only connects if the target is still inside the cone. Step out, dash through, or parry on the
 * beat. A stagger (a parry, a cross-slash finisher, a critical hit to its weak point) interrupts it.
 *
 * <p>Some attacks end in a lunge along the cone instead (a Vorpal Bunny's leap-chop, a wolf's
 * pounce, a Redcap's lunge after its feint). With more than two monsters on one player, the others
 * circle at a distance until one of the attackers steps back ({@link AttackTokens}).
 */
public class WindupMeleeGoal extends MeleeAttackGoal {
    /**
     * @param windup   ticks from the tell to the blow
     * @param animLead ticks before the blow at which the attack animation starts (its strike frame lands on the blow)
     * @param scale    damage multiplier
     * @param reach    how far the blow reaches from the monster's centre (also the cone's radius)
     * @param arc      width of the cone in degrees
     * @param lunge    speed of a lunge along the cone when the wind-up ends (0: it stands its ground)
     * @param heavy    a big blow: red cone and a blade glint
     */
    public record Attack(int windup, int animLead, float scale, float reach, float arc, double lunge, boolean heavy) {
        public static Attack swing(int windup, int animLead, float reach) {
            return new Attack(windup, animLead, 1f, reach, 100, 0, false);
        }
    }

    private static final int NORMAL_COLOR = 0xFFFF9A3A, HEAVY_COLOR = 0xFFFF3A3A;
    /** Ticks a lunge travels before its blow is checked. */
    private static final int LUNGE_TICKS = 4;

    private final FrontierMonster monster;
    private final double speed;
    private final Attack normal;
    @Nullable
    private final Attack special;
    private final float specialChance;
    /** Flying monsters circle this far above their target. */
    private double hoverHeight;

    private boolean running;
    @Nullable
    private Attack current;
    private int ticks;
    private int strikeAt;
    private float lockedYaw;
    @Nullable
    private LivingEntity tokenTarget;
    private int attacksWithToken;
    private int restTicks;
    private int circleTicks;
    private int circleSide = 1;

    public WindupMeleeGoal(FrontierMonster monster, double speed, Attack normal, @Nullable Attack special, float specialChance) {
        super(monster, speed, true);
        this.monster = monster;
        this.speed = speed;
        this.normal = normal;
        this.special = special;
        this.specialChance = specialChance;
    }

    public WindupMeleeGoal hovering(double height) {
        this.hoverHeight = height;
        return this;
    }

    public boolean isWindingUp() {
        return current != null;
    }

    @Override
    public boolean canUse() {
        return !Stun.isStunned(monster) && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = monster.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (target instanceof Player p && (p.isSpectator() || p.isCreative())) return false;
        return current != null || super.canContinueToUse();
    }

    @Override
    public void start() {
        super.start();
        running = true;
        current = null;
        restTicks = 0;
    }

    @Override
    public void stop() {
        super.stop();
        running = false;
        current = null;
        releaseToken();
    }

    @Override
    public void tick() {
        LivingEntity target = monster.getTarget();
        if (target == null) return;
        if (Stun.isStunned(monster)) {
            // Staggered: whatever it was winding up is lost.
            if (current != null) {
                current = null;
                resetAttackCooldown();
            }
            monster.getNavigation().stop();
            return;
        }
        if (current != null) {
            windupTick(target);
            return;
        }
        if (restTicks > 0) restTicks--;
        if (target instanceof Player && monster.distanceToSqr(target) < 7 * 7 && (restTicks > 0 || !acquireToken(target))) {
            circle(target);
            return;
        }
        super.tick();
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target) {
        if (canPerformAttack(target)) {
            begin(target, special != null && monster.getRandom().nextFloat() < specialChance ? special : normal);
        }
    }

    /** Starts a wind-up right now (a Redcap's lunge after its feint). @return false if it can't (busy, staggered, idle) */
    public boolean forceAttack(LivingEntity target, Attack attack) {
        if (!running || current != null || Stun.isStunned(monster)) return false;
        begin(target, attack);
        return true;
    }

    private void begin(LivingEntity target, Attack attack) {
        current = attack;
        ticks = 0;
        strikeAt = attack.windup() + (attack.lunge() > 0 ? LUNGE_TICKS : 0);
        lockedYaw = Fx.yawTo(monster.position(), target.position());
        monster.getNavigation().stop();
        face();
        // The cone lies on the target's ground (a hovering bee's sting lands below it).
        Vec3 at = new Vec3(monster.getX(), Math.min(monster.getY(), target.getY()), monster.getZ());
        float radius = attack.lunge() > 0 ? attack.reach() + (float) (attack.lunge() * 4) : attack.reach();
        float arc = attack.lunge() > 0 ? 36 : attack.arc();
        Fx.coneAt(monster.level(), at, lockedYaw, radius, arc, attack.heavy() ? HEAVY_COLOR : NORMAL_COLOR, strikeAt);
        if (attack.heavy()) Sfx.at(monster, ModSounds.BOSS_PARRY_GLINT, SoundSource.HOSTILE, 0.5f, 1.5f);
        if (attack.animLead() >= attack.windup()) monster.playAnim(FrontierMonster.ANIM_ATTACK);
    }

    private void windupTick(LivingEntity target) {
        Attack attack = current;
        ticks++;
        monster.getNavigation().stop();
        face();
        if (ticks < attack.windup()) {
            Vec3 v = monster.getDeltaMovement();
            monster.setDeltaMovement(0, monster.isNoGravity() ? v.y * 0.5 : v.y, 0);
        }
        if (ticks == attack.windup() - attack.animLead()) monster.playAnim(FrontierMonster.ANIM_ATTACK);
        if (ticks == attack.windup() && attack.lunge() > 0) {
            Vec3 dir = Vec3.directionFromRotation(0, lockedYaw);
            monster.setDeltaMovement(dir.x * attack.lunge(), monster.onGround() ? 0.25 : monster.getDeltaMovement().y, dir.z * attack.lunge());
        }
        if (ticks < strikeAt) return;
        current = null;
        resetAttackCooldown();
        if (target.isAlive() && inReach(target, attack)) monster.strike(getServerLevel(monster), target, attack.scale());
        afterAttack(target);
    }

    /** Whether the target is still inside the cone (or, after a lunge, within reach in front). */
    private boolean inReach(LivingEntity target, Attack attack) {
        double dy = target.getY() - monster.getY();
        if (dy > monster.getBbHeight() + 1.5 || dy < -(target.getBbHeight() + 1.5 + hoverHeight)) return false;
        Vec3 to = target.position().subtract(monster.position());
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        double gap = flat - target.getBbWidth() / 2;
        if (gap > attack.reach()) return false;
        if (gap < 0.4) return true;
        Vec3 facing = Vec3.directionFromRotation(0, lockedYaw);
        double cos = (facing.x * to.x + facing.z * to.z) / flat;
        double half = attack.lunge() > 0 ? 80 : attack.arc() / 2;
        return cos >= Math.cos(Math.toRadians(half));
    }

    private void face() {
        monster.setYRot(lockedYaw);
        monster.setYBodyRot(lockedYaw);
        monster.setYHeadRot(lockedYaw);
    }

    // ---- Taking turns ----------------------------------------------------------------

    private boolean acquireToken(LivingEntity target) {
        if (!AttackTokens.tryAcquire(monster, target)) return false;
        if (tokenTarget != target) {
            releaseToken();
            tokenTarget = target;
            attacksWithToken = 0;
        }
        return true;
    }

    private void releaseToken() {
        if (tokenTarget != null) AttackTokens.release(monster, tokenTarget);
        tokenTarget = null;
        attacksWithToken = 0;
    }

    /** After two blows, an attacker steps back and lets a waiting packmate in. */
    private void afterAttack(LivingEntity target) {
        if (tokenTarget != target) return;
        if (++attacksWithToken >= 2 && AttackTokens.othersWaiting(monster, target)) {
            releaseToken();
            restTicks = 40 + monster.getRandom().nextInt(30);
        }
    }

    /** Waiting for a turn: prowl around the target at a distance. */
    private void circle(LivingEntity target) {
        monster.getLookControl().setLookAt(target, 30f, 30f);
        if (--circleTicks > 0 && !monster.getNavigation().isDone()) return;
        circleTicks = 20 + monster.getRandom().nextInt(20);
        if (monster.getRandom().nextInt(4) == 0) circleSide = -circleSide;
        Vec3 from = monster.position().subtract(target.position());
        double angle = Math.atan2(from.z, from.x) + circleSide * Math.toRadians(35 + monster.getRandom().nextInt(25));
        double r = 4.5 + monster.getRandom().nextDouble() * 1.5;
        monster.getNavigation().moveTo(target.getX() + Math.cos(angle) * r, target.getY() + hoverHeight,
                target.getZ() + Math.sin(angle) * r, speed * 0.8);
    }
}
