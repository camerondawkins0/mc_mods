package com.slfrontier.entity.npc;

import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModEntities;
import com.slfrontier.world.Frontier;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Emul, the Vorpal Bunny. She finds the player after Lycagon marks them, invites them
 * to Rabituza, and later travels with them as a companion who casts Magic Edge at
 * whatever her partner is fighting.
 */
public class EmulEntity extends FrontierNpc {
    private static final DustParticleOptions MAGIC = new DustParticleOptions(0x9973FF, 1.2f);

    @Nullable
    private UUID owner;
    private int spellCooldown;

    public EmulEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    @Override
    protected boolean wanders() {
        return owner == null;
    }

    @Nullable
    public static EmulEntity findCompanion(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(EmulEntity.class, new AABB(player.blockPosition()).inflate(radius),
                e -> player.getUUID().equals(e.owner)).stream().findFirst().orElse(null);
    }

    /** Calls Emul to the player, replacing any copy that's still wandering around. */
    public static void summonFor(ServerPlayer player) {
        ServerLevel level = player.level();
        EmulEntity emul = findCompanion(player, 128);
        if (emul == null) {
            emul = ModEntities.EMUL.get().create(level, EntitySpawnReason.EVENT);
            if (emul == null) return;
            emul.setOwner(player.getUUID());
            emul.snapTo(player.getX() + 1, player.getY(), player.getZ() + 1, player.getYRot(), 0);
            level.addFreshEntity(emul);
        } else {
            emul.teleportTo(player.getX() + 1, player.getY(), player.getZ() + 1);
        }
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, emul.getX(), emul.getY() + 0.5, emul.getZ(), 20, 0.4, 0.4, 0.4, 0);
        level.playSound(null, emul.getX(), emul.getY(), emul.getZ(), SoundEvents.RABBIT_JUMP, SoundSource.NEUTRAL, 1f, 1.2f);
    }

    @Nullable
    private ServerPlayer ownerPlayer() {
        if (owner == null || !(level() instanceof ServerLevel server)) return null;
        return server.getServer().getPlayerList().getPlayer(owner);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || owner == null) return;
        ServerPlayer player = ownerPlayer();
        if (player == null || player.level() != level() || !ModAttachments.get(player).flag("emul_companion")) {
            // Partner logged out or left the Frontier: Emul goes home to Rabituza.
            if (tickCount % 100 == 0 && (player == null || !Frontier.isIn(player))) discard();
            return;
        }
        double dist = distanceToSqr(player);
        if (dist > 24 * 24) {
            teleportTo(player.getX(), player.getY(), player.getZ());
            getNavigation().stop();
        } else if (dist > 5 * 5 && tickCount % 10 == 0) {
            getNavigation().moveTo(player, 1.4);
        }
        if (spellCooldown > 0) spellCooldown--;
        LivingEntity target = player.getLastHurtMob();
        if (target == null || !target.isAlive() || player.tickCount - player.getLastHurtMobTimestamp() > 200) {
            target = player.getLastHurtByMob();
        }
        // She only casts at what she can actually see (no Magic Edge through walls or the ground).
        if (target != null && target.isAlive() && target != player && spellCooldown == 0 && distanceToSqr(target) < 16 * 16
                && target instanceof Mob && hasLineOfSight(target)) {
            int level = ModAttachments.get(player).level();
            castMagicEdge(target, 4f + level / 5f);
            spellCooldown = 40;
        }
    }

    /** Emul's attack spell: a blade of violet light that cuts straight to the target. */
    public void castMagicEdge(LivingEntity target, float damage) {
        if (!(level() instanceof ServerLevel server)) return;
        getLookControl().setLookAt(target);
        Vec3 from = position().add(0, getBbHeight() * 0.7, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        for (int i = 0; i <= 16; i++) {
            Vec3 p = from.lerp(to, i / 16.0);
            server.sendParticles(MAGIC, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
        }
        server.sendParticles(ParticleTypes.ENCHANTED_HIT, to.x, to.y, to.z, 12, 0.3, 0.3, 0.3, 0.2);
        ServerPlayer player = ownerPlayer();
        // An extra hit: it leaves the target's hit immunity alone, so it never eats her partner's next swing.
        com.slfrontier.skill.ExtraHit.deal(target, player != null ? damageSources().indirectMagic(this, player) : damageSources().magic(),
                damage, com.slfrontier.skill.ExtraHit.Kind.ALLY);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.NEUTRAL, 0.8f, 1.6f);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.store("Owner", net.minecraft.core.UUIDUtil.CODEC, owner);
    }

    @Override
    public void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.read("Owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
    }

    public boolean isPartnerOf(Player player) {
        return player.getUUID().equals(owner);
    }
}
