package com.slfrontier.entity.npc;

import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.fx.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Players' friends from the real world, who join the fight against the Tombguard: Arthur
 * Pencilgon with her golden spear and Oikatzo with his Spirit Fists. Like every NPC they
 * cannot be hurt; when a boss is near they close in and fight it, otherwise they behave
 * like townsfolk (they hang around the Serpent's Apple in Thirdrema).
 */
public class AllyNpc extends FrontierNpc {
    private static final double ENGAGE_RANGE = 28;
    private int attackCooldown = 20;
    @Nullable
    private FrontierBoss foe;

    public AllyNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    private boolean isPencilgon() {
        return "pencilgon".equals(npcId());
    }

    @Override
    protected boolean wanders() {
        return foe == null && super.wanders();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        if (tickCount % 20 == 0 && (foe == null || !foe.isAlive() || foe.distanceTo(this) > ENGAGE_RANGE * 1.5)) {
            foe = server.getEntitiesOfClass(FrontierBoss.class, new AABB(blockPosition()).inflate(ENGAGE_RANGE),
                    b -> b.isAlive() && b.getTarget() != null).stream().findFirst().orElse(null);
        }
        if (foe == null) return;
        getLookControl().setLookAt(foe, 30f, 30f);
        double reach = (isPencilgon() ? 3.6 : 2.4) + foe.getBbWidth() / 2;
        double dist = distanceTo(foe);
        if (dist > reach && tickCount % 10 == 0) getNavigation().moveTo(foe, 1.3);
        if (attackCooldown > 0) attackCooldown--;
        if (dist <= reach + 0.5 && attackCooldown == 0) strike(server, foe);
    }

    /** Pencilgon thrusts her spear (sometimes a spinning sweep); Oikatzo throws a one-two or an uppercut. */
    private void strike(ServerLevel server, LivingEntity target) {
        getNavigation().stop();
        boolean big = random.nextInt(4) == 0;
        startAnim(big ? 2 : 1, tickCount);
        server.broadcastEntityEvent(this, (byte) (ANIM_EVENT_BASE + (big ? 2 : 1)));
        float damage = isPencilgon() ? (big ? 9f : 6f) : (big ? 8f : 5f);
        target.invulnerableTime = 0;
        target.hurt(damageSources().mobAttack(this), damage);
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
        if (isPencilgon()) {
            if (big) Fx.arc(this, 3.5f, 0, 360, 0xFFFFD86A, 10);
            else Fx.send(server, new Fx.Spec(Fx.SPIRAL, (float) getX(), (float) getEyeY() - 0.3f, (float) getZ(), getYRot(), 0, 4,
                    0xFFFFD86A, 8, 0, 0));
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THROW.value(), getSoundSource(), 1f, 1.2f);
        } else {
            Fx.flash(server, at, big ? 2.2f : 1.2f, 0xFFFF6A3A, 6, 0);
            server.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, big ? 16 : 6, 0.3, 0.3, 0.3, 0.05);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, getSoundSource(), 1f, big ? 0.7f : 1.1f);
        }
        attackCooldown = big ? 45 : 28;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= ANIM_EVENT_BASE && id < ANIM_EVENT_BASE + MAX_ANIMS) {
            startAnim(id - ANIM_EVENT_BASE, tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }
}
