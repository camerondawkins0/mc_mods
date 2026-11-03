package com.slfrontier.entity;

import com.slfrontier.SLFrontier;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Base for regular Frontier monsters: a level, an exp reward and synced animations.
 *
 * <p>The level depends on where the monster spawns ({@link LeveledMobRegions}): a goblin on the
 * plains outside Firstia is level 2, one in the Thirdrema Highlands is level 14-18, with health,
 * damage and experience scaled to match. Monsters a boss summons (spawn reason MOB_SUMMONED) keep
 * their base level and give no experience, so a boss fight can't be farmed.
 */
public abstract class FrontierMonster extends Monster implements LeveledMob, SlfAnimated {
    public static final int ANIM_ATTACK = 1;
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(FrontierMonster.class, EntityDataSerializers.INT);
    private static final Identifier LEVEL_HEALTH = SLFrontier.id("region_level_health");
    private static final Identifier LEVEL_DAMAGE = SLFrontier.id("region_level_damage");
    private static final Identifier STRIKE = SLFrontier.id("strike_scale");
    /** Per level above the base: +8% health, +5% damage. */
    private static final double HEALTH_PER_LEVEL = 0.08, DAMAGE_PER_LEVEL = 0.05;

    private final AnimationState[] anims = SlfAnimated.create();
    private final int baseLevel;
    private final int baseExp;
    private boolean summoned;
    private boolean alerting;

    protected FrontierMonster(EntityType<? extends Monster> type, Level level, int frontierLevel, int frontierExp) {
        super(type, level);
        this.baseLevel = frontierLevel;
        this.baseExp = frontierExp;
        this.entityData.set(LEVEL, frontierLevel);
        this.xpReward = Math.max(1, frontierLevel / 2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LEVEL, 0);
    }

    @Override
    public int frontierLevel() {
        int level = entityData.get(LEVEL);
        return level > 0 ? level : baseLevel;
    }

    /** Experience for the kill: the base reward grows with the level; nothing for a boss's summons. */
    @Override
    public int frontierExp() {
        if (summoned) return 0;
        int level = frontierLevel();
        if (level <= baseLevel) return baseExp;
        return Math.round(baseExp * (float) Math.pow(level / (double) baseLevel, 0.9));
    }

    public int baseLevel() {
        return baseLevel;
    }

    public boolean isSummoned() {
        return summoned;
    }

    /** Sets the level and scales health and damage from the base level; heals to the new maximum. */
    public void setFrontierLevel(int level) {
        level = Math.max(baseLevel, level);
        entityData.set(LEVEL, level);
        int above = level - baseLevel;
        setModifier(Attributes.MAX_HEALTH, LEVEL_HEALTH, above * HEALTH_PER_LEVEL);
        setModifier(Attributes.ATTACK_DAMAGE, LEVEL_DAMAGE, above * DAMAGE_PER_LEVEL);
        xpReward = Math.max(1, level / 2);
        setHealth(getMaxHealth());
    }

    private void setModifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id, double amount) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        // Permanent, so it is saved with the attributes and loaded before the health.
        if (amount > 0) instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData data) {
        if (reason == EntitySpawnReason.MOB_SUMMONED) {
            summoned = true;
        } else if (reason != EntitySpawnReason.REINFORCEMENT) {
            setFrontierLevel(LeveledMobRegions.levelFor(this, level));
        }
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("FrontierLevel", frontierLevel());
        if (summoned) output.putBoolean("Summoned", true);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(LEVEL, Math.max(baseLevel, input.getIntOr("FrontierLevel", baseLevel)));
        summoned = input.getBooleanOr("Summoned", false);
    }

    @Override
    public AnimationState[] animStates() {
        return anims;
    }

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
        }
    }

    /** A melee blow at {@code scale} times the monster's attack damage (the wind-up goals land these). */
    public boolean strike(ServerLevel level, Entity target, float scale) {
        AttributeInstance attack = getAttribute(Attributes.ATTACK_DAMAGE);
        boolean scaled = attack != null && scale != 1f;
        if (scaled) attack.addOrUpdateTransientModifier(new AttributeModifier(STRIKE, scale - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        try {
            swing(InteractionHand.MAIN_HAND);
            return doHurtTarget(level, target);
        } finally {
            if (scaled) attack.removeModifier(STRIKE);
        }
    }

    // ---- Packs -----------------------------------------------------------------------

    /** How far this monster's call reaches when it spots a player (0: it hunts alone). */
    protected double alertRadius() {
        return 0;
    }

    /** A pack hunter that spots a player calls the others of its kind nearby. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity before = getTarget();
        super.setTarget(target);
        if (alerting || before != null || !(getTarget() instanceof Player player) || alertRadius() <= 0
                || !(level() instanceof ServerLevel server)) return;
        alerting = true;
        try {
            for (FrontierMonster friend : server.getEntitiesOfClass(FrontierMonster.class, getBoundingBox().inflate(alertRadius()),
                    m -> m != this && m.getType() == getType() && m.isAlive() && m.getTarget() == null)) {
                friend.setTarget(player);
            }
        } finally {
            alerting = false;
        }
    }
}
