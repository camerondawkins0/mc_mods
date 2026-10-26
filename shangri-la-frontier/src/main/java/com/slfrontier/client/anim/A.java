package com.slfrontier.client.anim;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * A compact builder for vanilla keyframe animations.
 * {@code rot("head", 0,0,0,0, 0.2f,-20,0,0)} reads as groups of (time, x, y, z) in degrees.
 */
public final class A {
    private final AnimationDefinition.Builder builder;

    private A(float length) {
        this.builder = AnimationDefinition.Builder.withLength(length);
    }

    public static A of(float seconds) {
        return new A(seconds);
    }

    public A loop() {
        builder.looping();
        return this;
    }

    public A rot(String part, float... txyz) {
        builder.addAnimation(part, new AnimationChannel(AnimationChannel.Targets.ROTATION, frames(txyz, true)));
        return this;
    }

    public A pos(String part, float... txyz) {
        builder.addAnimation(part, new AnimationChannel(AnimationChannel.Targets.POSITION, frames(txyz, false)));
        return this;
    }

    public A scale(String part, float... txyz) {
        Keyframe[] frames = new Keyframe[txyz.length / 4];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new Keyframe(txyz[i * 4], KeyframeAnimations.scaleVec(txyz[i * 4 + 1], txyz[i * 4 + 2], txyz[i * 4 + 3]),
                    AnimationChannel.Interpolations.LINEAR);
        }
        builder.addAnimation(part, new AnimationChannel(AnimationChannel.Targets.SCALE, frames));
        return this;
    }

    private static Keyframe[] frames(float[] txyz, boolean rotation) {
        if (txyz.length % 4 != 0) throw new IllegalArgumentException("Keyframes come in groups of four");
        Keyframe[] frames = new Keyframe[txyz.length / 4];
        for (int i = 0; i < frames.length; i++) {
            float t = txyz[i * 4];
            float x = txyz[i * 4 + 1], y = txyz[i * 4 + 2], z = txyz[i * 4 + 3];
            frames[i] = new Keyframe(t, rotation ? KeyframeAnimations.degreeVec(x, y, z) : KeyframeAnimations.posVec(x, y, z),
                    AnimationChannel.Interpolations.CATMULLROM);
        }
        return frames;
    }

    public AnimationDefinition build() {
        return builder.build();
    }
}
