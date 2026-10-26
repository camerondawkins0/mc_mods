package com.slfrontier.entity;

import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** Base for regular Frontier monsters: a level, an exp reward and synced animations. */
public abstract class FrontierMonster extends Monster implements LeveledMob, SlfAnimated {
    public static final int ANIM_ATTACK = 1;

    private final AnimationState[] anims = SlfAnimated.create();
    private final int frontierLevel;
    private final int frontierExp;

    protected FrontierMonster(EntityType<? extends Monster> type, Level level, int frontierLevel, int frontierExp) {
        super(type, level);
        this.frontierLevel = frontierLevel;
        this.frontierExp = frontierExp;
        this.xpReward = Math.max(1, frontierLevel / 2);
    }

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

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) playAnim(ANIM_ATTACK);
        return hit;
    }
}
