package com.slfrontier.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** A status effect whose behaviour lives in attribute modifiers or event handlers. */
public class SimpleEffect extends MobEffect {
    public SimpleEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
