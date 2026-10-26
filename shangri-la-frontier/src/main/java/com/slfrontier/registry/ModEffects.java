package com.slfrontier.registry;

import com.slfrontier.SLFrontier;
import com.slfrontier.effect.SimpleEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SLFrontier.MODID);

    /** Applied after dying inside the Frontier. */
    public static final DeferredHolder<MobEffect, MobEffect> DEATH_PENALTY = EFFECTS.register("death_penalty",
            () -> new SimpleEffect(MobEffectCategory.HARMFUL, 0x5A5A6E)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, SLFrontier.id("effect.death_penalty_speed"), -0.15,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, SLFrontier.id("effect.death_penalty_damage"), -0.2,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Holy Water: undead that are drenched lose their auras. */
    public static final DeferredHolder<MobEffect, MobEffect> DRENCHED = EFFECTS.register("drenched",
            () -> new SimpleEffect(MobEffectCategory.HARMFUL, 0x9FE2FF));

    private ModEffects() {
    }
}
