package com.slfrontier.client.anim;

import net.minecraft.client.animation.AnimationDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * Animations for one creature. {@code slots[0]} is the looping idle; the rest match
 * the animation slots the entity triggers from the server.
 */
public record AnimSet(@Nullable AnimationDefinition walk, float walkSpeed, float walkScale,
                      @Nullable String headPart, AnimationDefinition... slots) {
}
