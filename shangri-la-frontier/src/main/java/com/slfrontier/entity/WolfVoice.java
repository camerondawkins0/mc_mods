package com.slfrontier.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;

/**
 * Wolf sounds come in variant sets now. Lycagon, its shadow and the shadow wolves all use the deep
 * "big" set; there is no howl any more, so the whine (played low, as callers do) stands in for it.
 */
public final class WolfVoice {
    private static final WolfSoundVariant.WolfSoundSet BIG = SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds();

    public static final SoundEvent GROWL = BIG.growlSound().value();
    public static final SoundEvent HOWL = BIG.whineSound().value();
    public static final SoundEvent HURT = BIG.hurtSound().value();
    public static final SoundEvent DEATH = BIG.deathSound().value();

    private WolfVoice() {
    }
}
