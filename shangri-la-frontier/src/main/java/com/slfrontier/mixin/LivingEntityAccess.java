package com.slfrontier.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The damage a living entity took on its last hit, which vanilla compares against during its
 * hit-immunity ticks. Extra hits (the off-hand blade, skills, Emul, allies) put it back afterwards
 * so they never eat the player's next swing (see skill.ExtraHit).
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccess {
    @Accessor("lastHurt")
    float slfrontier$getLastHurt();

    @Accessor("lastHurt")
    void slfrontier$setLastHurt(float value);
}
