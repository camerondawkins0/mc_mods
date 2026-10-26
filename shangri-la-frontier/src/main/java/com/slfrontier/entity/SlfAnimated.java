package com.slfrontier.entity;

import net.minecraft.world.entity.AnimationState;

/**
 * Entities whose keyframe animations are triggered from the server with an entity
 * event. Slot 0 is reserved for the looping idle animation.
 */
public interface SlfAnimated {
    int ANIM_EVENT_BASE = 90;
    int MAX_ANIMS = 8;

    AnimationState[] animStates();

    /** Starts the animation in {@code slot} on the client, stopping any other one-shot. */
    default void startAnim(int slot, int tickCount) {
        AnimationState[] states = animStates();
        for (int i = 1; i < states.length; i++) {
            if (i != slot) states[i].stop();
        }
        if (slot > 0 && slot < states.length) states[slot].start(tickCount);
    }

    static AnimationState[] create() {
        AnimationState[] states = new AnimationState[MAX_ANIMS];
        for (int i = 0; i < states.length; i++) states[i] = new AnimationState();
        return states;
    }
}
