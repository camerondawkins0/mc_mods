package com.slfrontier.entity.npc;

import com.slfrontier.quest.Story;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A story NPC. NPCs can't be hurt, never despawn, wander close to where they were
 * placed, and open a dialogue when you talk to them (use / right-click).
 */
public class FrontierNpc extends PathfinderMob implements com.slfrontier.entity.SlfAnimated {
    private final net.minecraft.world.entity.AnimationState[] anims = com.slfrontier.entity.SlfAnimated.create();
    private BlockPos home;

    public FrontierNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (isGhost()) setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    /** The NPC's id, e.g. "frontier_guide" or "vysache". */
    public String npcId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath();
    }

    public boolean isGhost() {
        return "setsuna".equals(npcId());
    }

    protected boolean wanders() {
        return !isGhost();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        if (wanders()) goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5) {
            @Override
            public boolean canUse() {
                return super.canUse() && (home == null || home.closerToCenterThan(position(), 8));
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public net.minecraft.world.entity.AnimationState[] animStates() {
        return anims;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) anims[0].startIfStopped(tickCount);
        if (!level().isClientSide && home == null) home = blockPosition();
        if (!level().isClientSide && home != null && wanders() && !home.closerToCenterThan(position(), 14) && tickCount % 40 == 0) {
            getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.6);
        }
        if (isGhost() && level() instanceof ServerLevel server && tickCount % 8 == 0) {
            server.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + 1, getZ(), 1, 0.4, 0.6, 0.4, 0);
        }
        if (isGhost()) {
            setDeltaMovement(0, Math.sin(tickCount / 20.0) * 0.01, 0);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            getNavigation().stop();
            getLookControl().setLookAt(player);
            Story.onTalk(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "Home").ifPresent(pos -> home = pos);
    }
}
