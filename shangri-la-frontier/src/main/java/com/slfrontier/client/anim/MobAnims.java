package com.slfrontier.client.anim;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.util.Mth;

/**
 * Keyframe animations for every creature and NPC. Times are in seconds, rotations in
 * degrees and positions in pixels (y up). Slot numbers match the attack ids each entity
 * sends (attack id + 1); slot 0 is the looping idle.
 *
 * <p>Rotation conventions (model space): positive x swings parts below the pivot
 * backward and parts above it forward; a negative x on a head that points forward
 * lifts the snout. Positive y turns to the creature's right. Positive z moves parts
 * that hang below the pivot toward the creature's right side.
 */
public final class MobAnims {
    private MobAnims() {
    }

    /** A sine wave sampled into {@code steps} keyframes over one loop ({@code length} seconds). */
    private static float[] wave(float length, float amplitude, float phase, int axis, int steps) {
        float[] k = new float[(steps + 1) * 4];
        for (int j = 0; j <= steps; j++) {
            float t = length * j / steps;
            float v = amplitude * Mth.sin(Mth.TWO_PI * j / steps - phase);
            k[j * 4] = t;
            k[j * 4 + 1 + axis] = v;
        }
        return k;
    }

    // ==== Lycagon, its shadow and the shadow wolves ===================================

    private static final AnimationDefinition WOLF_WALK = A.of(0.9f).loop()
            .rot("front_left_leg", 0, 25, 0, 0, 0.45f, -25, 0, 0, 0.9f, 25, 0, 0)
            .rot("front_left_shin", 0, 0, 0, 0, 0.2f, 35, 0, 0, 0.45f, 0, 0, 0, 0.9f, 0, 0, 0)
            .rot("front_right_leg", 0, -25, 0, 0, 0.45f, 25, 0, 0, 0.9f, -25, 0, 0)
            .rot("front_right_shin", 0, 0, 0, 0, 0.45f, 0, 0, 0, 0.65f, 35, 0, 0, 0.9f, 0, 0, 0)
            .rot("back_right_leg", 0, 25, 0, 0, 0.45f, -25, 0, 0, 0.9f, 25, 0, 0)
            .rot("back_right_shin", 0, 0, 0, 0, 0.2f, 22, 0, 0, 0.45f, 0, 0, 0, 0.9f, 0, 0, 0)
            .rot("back_left_leg", 0, -25, 0, 0, 0.45f, 25, 0, 0, 0.9f, -25, 0, 0)
            .rot("back_left_shin", 0, 0, 0, 0, 0.45f, 0, 0, 0, 0.65f, 22, 0, 0, 0.9f, 0, 0, 0)
            .pos("body", 0, 0, 0, 0, 0.225f, 0, 1, 0, 0.45f, 0, 0, 0, 0.675f, 0, 1, 0, 0.9f, 0, 0, 0)
            .rot("body", 0, 0, 0, -2, 0.45f, 0, 0, 2, 0.9f, 0, 0, -2)
            .rot("head", 0, 0, 0, 0, 0.225f, 4, 0, 0, 0.45f, 0, 0, 0, 0.675f, 4, 0, 0, 0.9f, 0, 0, 0)
            .rot("tail", 0, 0, -8, 0, 0.45f, 0, 8, 0, 0.9f, 0, -8, 0)
            .rot("tail2", 0, 0, -6, 0, 0.225f, 0, -10, 0, 0.675f, 0, 10, 0, 0.9f, 0, -6, 0)
            .rot("tail3", 0, 0, 0, 0, 0.45f, 0, -12, 0, 0.9f, 0, 0, 0)
            .build();

    private static final AnimationDefinition WOLF_IDLE = A.of(3f).loop()
            .pos("body", 0, 0, 0, 0, 1.5f, 0, -0.6f, 0, 3f, 0, 0, 0)
            .pos("mane", 0, 0, 0, 0, 1.5f, 0, 0.4f, 0, 3f, 0, 0, 0)
            .rot("head", 0, 4, 0, 0, 1.5f, 7, 0, 0, 3f, 4, 0, 0)
            .rot("jaw", 0, 0, 0, 0, 1.4f, 5, 0, 0, 1.8f, 0, 0, 0, 3f, 0, 0, 0)
            .rot("tail", 0, 0, -6, 0, 1.5f, 0, 6, 0, 3f, 0, -6, 0)
            .rot("tail2", 0, 0, -4, 0, 0.75f, 0, -8, 0, 2.25f, 0, 8, 0, 3f, 0, -4, 0)
            .rot("tail3", 0, 0, 0, 0, 1.5f, 0, -10, 0, 3f, 0, 0, 0)
            .rot("left_ear", 0, 0, 0, 0, 2.2f, 0, 0, 0, 2.3f, -12, 0, 0, 2.45f, 0, 0, 0, 3f, 0, 0, 0)
            .rot("right_ear", 0, 0, 0, 0, 0.8f, 0, 0, 0, 0.9f, -12, 0, 0, 1.05f, 0, 0, 0, 3f, 0, 0, 0)
            .build();

    private static final AnimationDefinition WOLF_BITE = A.of(0.7f)
            .rot("head", 0, 0, 0, 0, 0.2f, -18, 0, 0, 0.38f, 22, 0, 0, 0.7f, 0, 0, 0)
            .rot("jaw", 0, 0, 0, 0, 0.22f, 38, 0, 0, 0.38f, 0, 0, 0, 0.7f, 0, 0, 0)
            .rot("mane", 0, 0, 0, 0, 0.2f, -6, 0, 0, 0.38f, 8, 0, 0, 0.7f, 0, 0, 0)
            .pos("body", 0, 0, 0, 0, 0.2f, 0, 0, 1.5f, 0.38f, 0, 0, -3, 0.7f, 0, 0, 0)
            .build();

    public static final AnimSet LYCAGON = new AnimSet(WOLF_WALK, 1.4f, 30f, "head",
            WOLF_IDLE,
            // 1: bite
            WOLF_BITE,
            // 2: claw combo with the right paw
            A.of(1.2f)
                    .rot("front_right_leg", 0, 0, 0, 0, 0.3f, -85, 0, -10, 0.45f, 30, 0, 0, 0.7f, -85, 0, 10, 0.85f, 30, 0, 0, 1.2f, 0, 0, 0)
                    .rot("front_right_shin", 0, 0, 0, 0, 0.3f, -30, 0, 0, 0.45f, 10, 0, 0, 0.7f, -30, 0, 0, 0.85f, 10, 0, 0, 1.2f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.3f, -10, 0, 0, 0.45f, 5, 0, 0, 0.7f, -10, 0, 0, 0.85f, 5, 0, 0, 1.2f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.3f, 0, 15, 0, 0.45f, 8, -10, 0, 0.7f, 0, -15, 0, 0.85f, 8, 10, 0, 1.2f, 0, 0, 0)
                    .build(),
            // 3: shadow dash / pounce
            A.of(1.1f)
                    .pos("body", 0, 0, 0, 0, 0.45f, 0, -4, 0, 0.6f, 0, 2, -2, 1.1f, 0, 0, 0)
                    .rot("body", 0.45f, 0, 0, 0, 0.6f, -20, 0, 0, 1.1f, 0, 0, 0)
                    .rot("front_left_leg", 0.45f, 0, 0, 0, 0.6f, -55, 0, 0, 1.1f, 0, 0, 0)
                    .rot("front_right_leg", 0.45f, 0, 0, 0, 0.6f, -55, 0, 0, 1.1f, 0, 0, 0)
                    .rot("back_left_leg", 0.45f, 0, 0, 0, 0.6f, 45, 0, 0, 1.1f, 0, 0, 0)
                    .rot("back_right_leg", 0.45f, 0, 0, 0, 0.6f, 45, 0, 0, 1.1f, 0, 0, 0)
                    .rot("tail", 0.45f, 0, 0, 0, 0.6f, -30, 0, 0, 1.1f, 0, 0, 0)
                    .rot("jaw", 0.45f, 0, 0, 0, 0.6f, 30, 0, 0, 0.8f, 0, 0, 0)
                    .build(),
            // 4: howl
            A.of(1.5f)
                    .rot("head", 0, 0, 0, 0, 0.4f, -48, 0, 0, 1.2f, -48, 0, 0, 1.5f, 0, 0, 0)
                    .rot("jaw", 0, 0, 0, 0, 0.4f, 35, 0, 0, 1.2f, 35, 0, 0, 1.5f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.4f, -12, 0, 0, 1.2f, -12, 0, 0, 1.5f, 0, 0, 0)
                    .rot("mane", 0, 0, 0, 0, 0.4f, -10, 0, 0, 1.2f, -10, 0, 0, 1.5f, 0, 0, 0)
                    .rot("left_ear", 0, 0, 0, 0, 0.4f, 20, 0, 0, 1.2f, 20, 0, 0, 1.5f, 0, 0, 0)
                    .rot("right_ear", 0, 0, 0, 0, 0.4f, 20, 0, 0, 1.2f, 20, 0, 0, 1.5f, 0, 0, 0)
                    .build(),
            // 5: night rend (rear up and slam)
            A.of(1.8f)
                    .rot("body", 0, 0, 0, 0, 0.3f, -32, 0, 0, 1.2f, -32, 0, 0, 1.35f, 15, 0, 0, 1.8f, 0, 0, 0)
                    .rot("front_left_leg", 0, 0, 0, 0, 0.3f, -70, 0, 0, 1.2f, -70, 0, 0, 1.35f, 30, 0, 0, 1.8f, 0, 0, 0)
                    .rot("front_right_leg", 0, 0, 0, 0, 0.3f, -70, 0, 0, 1.2f, -70, 0, 0, 1.35f, 30, 0, 0, 1.8f, 0, 0, 0)
                    .rot("front_left_shin", 0, 0, 0, 0, 0.3f, -25, 0, 0, 1.2f, -25, 0, 0, 1.35f, 0, 0, 0)
                    .rot("front_right_shin", 0, 0, 0, 0, 0.3f, -25, 0, 0, 1.2f, -25, 0, 0, 1.35f, 0, 0, 0)
                    .rot("back_left_leg", 0, 0, 0, 0, 0.3f, 30, 0, 0, 1.2f, 30, 0, 0, 1.35f, 0, 0, 0)
                    .rot("back_right_leg", 0, 0, 0, 0, 0.3f, 30, 0, 0, 1.2f, 30, 0, 0, 1.35f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.3f, -25, 0, 0, 1.2f, -25, 0, 0, 1.35f, 20, 0, 0, 1.8f, 0, 0, 0)
                    .rot("jaw", 0, 0, 0, 0, 1.2f, 30, 0, 0, 1.35f, 0, 0, 0)
                    .build(),
            // 6: vanish (melts into the shadows)
            A.of(1.2f)
                    .pos("body", 0, 0, 0, 0, 0.4f, 0, -3, 0, 1.2f, 0, -3, 0)
                    .rot("head", 0, 0, 0, 0, 0.4f, 15, 0, 0, 1.2f, 15, 0, 0)
                    .scale("root", 0, 1, 1, 1, 0.4f, 1, 1, 1, 1.0f, 1.25f, 0.1f, 1.25f, 1.2f, 1.25f, 0.1f, 1.25f)
                    .build(),
            // 7: shadow clone (shudders and splits off its shadow)
            A.of(1.6f)
                    .rot("body", 0, 0, 0, 0, 0.2f, 0, 0, 4, 0.4f, 0, 0, -4, 0.6f, 0, 0, 4, 0.8f, 0, 0, -4, 1.0f, 0, 0, 3, 1.6f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.3f, -22, 0, 0, 1.2f, -22, 0, 0, 1.6f, 0, 0, 0)
                    .rot("jaw", 0, 0, 0, 0, 0.3f, 22, 0, 0, 1.2f, 22, 0, 0, 1.6f, 0, 0, 0)
                    .scale("mane", 0, 1, 1, 1, 0.8f, 1.12f, 1.12f, 1.12f, 1.6f, 1, 1, 1)
                    .build());

    public static final AnimSet SHADOW_WOLF = new AnimSet(WOLF_WALK, 2.2f, 40f, "head", WOLF_IDLE, WOLF_BITE);

    // ==== Wezaemon the Tombguard =======================================================

    public static final AnimSet WEZAEMON = new AnimSet(
            A.of(1.2f).loop()
                    .rot("left_leg", 0, 25, 0, 0, 0.6f, -25, 0, 0, 1.2f, 25, 0, 0)
                    .rot("right_leg", 0, -25, 0, 0, 0.6f, 25, 0, 0, 1.2f, -25, 0, 0)
                    .rot("left_arm", 0, -15, 0, 0, 0.6f, 15, 0, 0, 1.2f, -15, 0, 0)
                    .rot("right_arm", 0, 5, 0, 0, 0.6f, -5, 0, 0, 1.2f, 5, 0, 0)
                    .rot("ribbon", 0, 8, 0, 0, 0.6f, 14, 0, 0, 1.2f, 8, 0, 0)
                    .rot("ribbon2", 0, 6, 0, 0, 0.3f, 12, 0, 0, 0.9f, 4, 0, 0, 1.2f, 6, 0, 0)
                    .build(),
            1.5f, 40f, "head",
            A.of(4f).loop()
                    .rot("body", 0, 0, 0, 0, 2f, 2, 0, 0, 4f, 0, 0, 0)
                    .rot("right_arm", 0, -10, 0, 0, 2f, -14, 0, 0, 4f, -10, 0, 0)
                    .rot("ribbon", 0, 0, 0, -4, 2f, 3, 0, 4, 4f, 0, 0, -4)
                    .rot("ribbon2", 0, 0, 0, -6, 1f, 2, 0, -8, 3f, 4, 0, 8, 4f, 0, 0, -6)
                    .build(),
            // 1: iaijutsu dash
            A.of(1.4f)
                    .pos("root", 0, 0, 0, 0, 0.5f, 0, -2, 0, 0.6f, 0, -2, 0, 1.0f, 0, 0, 0)
                    .rot("right_arm", 0, 0, 0, 0, 0.5f, -30, 40, 0, 0.6f, -30, 40, 0, 0.7f, -85, -70, 0, 1.0f, -80, -70, 0, 1.4f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.5f, 10, 20, 0, 0.7f, 0, -30, 0, 1.0f, 0, -30, 0, 1.4f, 0, 0, 0)
                    .rot("ribbon", 0.6f, 0, 0, 0, 0.75f, 50, 0, 0, 1.4f, 0, 0, 0)
                    .build(),
            // 2: triple cut
            A.of(1.6f)
                    .rot("right_arm", 0, 0, 0, 0, 0.3f, -110, 0, 0, 0.4f, -10, 0, 0,
                            0.7f, -120, 30, 0, 0.8f, -20, -40, 0, 1.1f, -95, 0, 0, 1.25f, -5, 0, 0, 1.6f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.4f, 8, 0, 0, 0.8f, 0, -20, 0, 1.25f, 10, 0, 0, 1.6f, 0, 0, 0)
                    .build(),
            // 3: Sky Clear (raise, hold, then a full spin)
            A.of(2.1f)
                    .rot("right_arm", 0, 0, 0, 0, 0.5f, -170, 0, 0, 1.5f, -170, 0, 0, 1.6f, -90, 0, 0, 1.85f, -90, 0, 0, 2.1f, 0, 0, 0)
                    .rot("root", 0, 0, 0, 0, 1.55f, 0, 0, 0, 1.6f, 0, -90, 0, 1.68f, 0, -180, 0, 1.76f, 0, -270, 0, 1.85f, 0, -360, 0, 2.1f, 0, -360, 0)
                    .rot("ribbon", 1.55f, 0, 0, 0, 1.7f, 60, 0, 0, 2.1f, 0, 0, 0)
                    .build(),
            // 4: stagger after being parried
            A.of(5f)
                    .rot("body", 0, 0, 0, 0, 0.3f, -15, 0, 0, 4.5f, -15, 0, 0, 5f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.3f, -20, 0, 0, 4.5f, -20, 0, 0, 5f, 0, 0, 0)
                    .rot("right_arm", 0, 0, 0, 0, 0.3f, 15, 0, 20, 4.5f, 15, 0, 20, 5f, 0, 0, 0)
                    .rot("left_arm", 0, 0, 0, 0, 0.3f, 15, 0, -20, 4.5f, 15, 0, -20, 5f, 0, 0, 0)
                    .pos("root", 0, 0, 0, 0, 0.3f, 0, -2, 0, 4.5f, 0, -2, 0, 5f, 0, 0, 0)
                    .build(),
            // 5: Tachikaze, a wide horizontal cut that releases a flying wind blade
            A.of(1.3f)
                    .rot("right_arm", 0, 0, 0, 0, 0.4f, -70, 75, 0, 0.55f, -90, -80, 0, 1.0f, -85, -80, 0, 1.3f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.4f, 0, 30, 0, 0.55f, 0, -40, 0, 1.0f, 0, -35, 0, 1.3f, 0, 0, 0)
                    .rot("left_leg", 0, 0, 0, 0, 0.4f, -20, 0, 0, 1.0f, -20, 0, 0, 1.3f, 0, 0, 0)
                    .rot("ribbon", 0.5f, 0, 0, 0, 0.65f, 45, 0, -20, 1.3f, 0, 0, 0)
                    .build(),
            // 6: Raisho, a lightning-fast lunging thrust
            A.of(1.6f)
                    .pos("root", 0, 0, 0, 0, 0.3f, 0, -1.5f, 0, 0.4f, 0, -1, -3, 1.2f, 0, -1, -3, 1.6f, 0, 0, 0)
                    .rot("right_arm", 0, 0, 0, 0, 0.3f, -40, 30, 0, 0.4f, -90, 0, 0, 1.2f, -90, 0, 0, 1.6f, 0, 0, 0)
                    .rot("left_arm", 0, 0, 0, 0, 0.4f, -40, 0, 0, 1.2f, -40, 0, 0, 1.6f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.4f, 15, 0, 0, 1.2f, 15, 0, 0, 1.6f, 0, 0, 0)
                    .rot("right_leg", 0, 0, 0, 0, 0.4f, -35, 0, 0, 1.2f, -35, 0, 0, 1.6f, 0, 0, 0)
                    .rot("left_leg", 0, 0, 0, 0, 0.4f, 30, 0, 0, 1.2f, 30, 0, 0, 1.6f, 0, 0, 0)
                    .rot("ribbon", 0.35f, 0, 0, 0, 0.5f, 70, 0, 0, 1.6f, 0, 0, 0)
                    .build(),
            // 7: Tensei, the phase-three heaven-splitting cleave
            A.of(2.6f)
                    .rot("right_arm", 0, 0, 0, 0, 0.8f, -170, 0, 15, 1.8f, -175, 0, 15, 2.0f, -25, 0, 0, 2.3f, -25, 0, 0, 2.6f, 0, 0, 0)
                    .rot("left_arm", 0, 0, 0, 0, 0.8f, -150, 0, -25, 1.8f, -155, 0, -25, 2.0f, -30, 0, 0, 2.3f, -30, 0, 0, 2.6f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.8f, -10, 0, 0, 1.8f, -12, 0, 0, 2.0f, 25, 0, 0, 2.3f, 25, 0, 0, 2.6f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.8f, -15, 0, 0, 1.8f, -15, 0, 0, 2.0f, 10, 0, 0, 2.6f, 0, 0, 0)
                    .pos("root", 0, 0, 0, 0, 0.8f, 0, 1, 0, 1.8f, 0, 1.5f, 0, 2.0f, 0, -2.5f, 0, 2.3f, 0, -2.5f, 0, 2.6f, 0, 0, 0)
                    .rot("ribbon", 0, 0, 0, 0, 1.8f, 30, 0, 0, 2.05f, 80, 0, 0, 2.6f, 0, 0, 0)
                    .build());

    // ==== Kirin ========================================================================

    public static final AnimSet KIRIN = new AnimSet(
            A.of(0.6f).loop()
                    .rot("front_left_leg", 0, 35, 0, 0, 0.3f, -35, 0, 0, 0.6f, 35, 0, 0)
                    .rot("front_right_leg", 0, 20, 0, 0, 0.1f, 35, 0, 0, 0.4f, -35, 0, 0, 0.6f, 20, 0, 0)
                    .rot("back_left_leg", 0, -35, 0, 0, 0.3f, 35, 0, 0, 0.6f, -35, 0, 0)
                    .rot("back_right_leg", 0, -20, 0, 0, 0.1f, -35, 0, 0, 0.4f, 35, 0, 0, 0.6f, -20, 0, 0)
                    .pos("body", 0, 0, 0, 0, 0.15f, 0, 1.2f, 0, 0.3f, 0, 0, 0, 0.45f, 0, 1.2f, 0, 0.6f, 0, 0, 0)
                    .rot("neck", 0, 0, 0, 0, 0.15f, 6, 0, 0, 0.3f, 0, 0, 0, 0.45f, 6, 0, 0, 0.6f, 0, 0, 0)
                    .rot("tail", 0, 15, 0, 0, 0.3f, 25, 0, 0, 0.6f, 15, 0, 0)
                    .build(),
            1.0f, 40f, "head",
            A.of(3f).loop()
                    .rot("neck", 0, 0, 0, 0, 1.5f, 4, 0, 0, 3f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 1.5f, -4, 0, 0, 3f, 0, 0, 0)
                    .rot("tail", 0, 0, -12, 0, 1.5f, 0, 12, 0, 3f, 0, -12, 0)
                    .rot("left_blade_ear", 0, 0, 0, 0, 2f, 0, 0, 0, 2.15f, -15, 0, 0, 2.3f, 0, 0, 0, 3f, 0, 0, 0)
                    .rot("right_blade_ear", 0, 0, 0, 0, 2f, 0, 0, 0, 2.15f, -15, 0, 0, 2.3f, 0, 0, 0, 3f, 0, 0, 0)
                    .build(),
            // 1: charge (head down, trampling)
            A.of(1.2f)
                    .rot("neck", 0, 0, 0, 0, 0.3f, 25, 0, 0, 1.0f, 25, 0, 0, 1.2f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.3f, 15, 0, 0, 1.0f, 15, 0, 0, 1.2f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.3f, 6, 0, 0, 1.0f, 6, 0, 0, 1.2f, 0, 0, 0)
                    .rot("tail", 0, 0, 0, 0, 0.3f, 35, 0, 0, 1.0f, 35, 0, 0, 1.2f, 0, 0, 0)
                    .build(),
            // 2: missile barrage (rears up; the back fin launches)
            A.of(1.6f)
                    .rot("body", 0, 0, 0, 0, 0.4f, -25, 0, 0, 1.2f, -25, 0, 0, 1.6f, 0, 0, 0)
                    .rot("front_left_leg", 0, 0, 0, 0, 0.4f, -60, 0, 0, 0.6f, -40, 0, 0, 0.8f, -65, 0, 0, 1.2f, -60, 0, 0, 1.6f, 0, 0, 0)
                    .rot("front_right_leg", 0, 0, 0, 0, 0.4f, -45, 0, 0, 0.6f, -65, 0, 0, 0.8f, -40, 0, 0, 1.2f, -45, 0, 0, 1.6f, 0, 0, 0)
                    .rot("back_left_leg", 0, 0, 0, 0, 0.4f, 25, 0, 0, 1.2f, 25, 0, 0, 1.6f, 0, 0, 0)
                    .rot("back_right_leg", 0, 0, 0, 0, 0.4f, 25, 0, 0, 1.2f, 25, 0, 0, 1.6f, 0, 0, 0)
                    .rot("neck", 0, 0, 0, 0, 0.4f, -10, 0, 0, 1.2f, -10, 0, 0, 1.6f, 0, 0, 0)
                    .scale("back_fin", 0, 1, 1, 1, 0.5f, 1, 1.4f, 1, 0.6f, 1, 1, 1, 0.7f, 1, 1.4f, 1, 0.8f, 1, 1, 1, 1.6f, 1, 1, 1)
                    .build(),
            // 3: laser sweep from the head
            A.of(1.4f)
                    .rot("neck", 0, 0, 0, 0, 0.3f, -12, 0, 0, 1.1f, -12, 0, 0, 1.4f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.3f, 5, -40, 0, 1.1f, 5, 40, 0, 1.4f, 0, 0, 0)
                    .build(),
            // 4: lightning rain (rears high, looking skyward)
            A.of(1.8f)
                    .rot("body", 0, 0, 0, 0, 0.5f, -35, 0, 0, 1.4f, -35, 0, 0, 1.8f, 0, 0, 0)
                    .rot("front_left_leg", 0, 0, 0, 0, 0.5f, -70, 0, 0, 1.4f, -70, 0, 0, 1.8f, 0, 0, 0)
                    .rot("front_right_leg", 0, 0, 0, 0, 0.5f, -70, 0, 0, 1.4f, -70, 0, 0, 1.8f, 0, 0, 0)
                    .rot("back_left_leg", 0, 0, 0, 0, 0.5f, 35, 0, 0, 1.4f, 35, 0, 0, 1.8f, 0, 0, 0)
                    .rot("back_right_leg", 0, 0, 0, 0, 0.5f, 35, 0, 0, 1.4f, 35, 0, 0, 1.8f, 0, 0, 0)
                    .rot("neck", 0, 0, 0, 0, 0.5f, -20, 0, 0, 1.4f, -20, 0, 0, 1.8f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.5f, -30, 0, 0, 1.4f, -30, 0, 0, 1.8f, 0, 0, 0)
                    .build());

    // ==== Vorpal Bunnies ===============================================================

    public static final AnimSet VORPAL_BUNNY = new AnimSet(
            A.of(0.5f).loop()
                    .pos("body", 0, 0, 0, 0, 0.15f, 0, 2, 0, 0.3f, 0, 0.5f, 0, 0.5f, 0, 0, 0)
                    .rot("left_leg", 0, 20, 0, 0, 0.15f, -30, 0, 0, 0.3f, 10, 0, 0, 0.5f, 20, 0, 0)
                    .rot("right_leg", 0, 20, 0, 0, 0.15f, -30, 0, 0, 0.3f, 10, 0, 0, 0.5f, 20, 0, 0)
                    .rot("left_arm", 0, -10, 0, 0, 0.15f, 15, 0, 0, 0.5f, -10, 0, 0)
                    .rot("left_ear", 0, 10, 0, 0, 0.15f, -10, 0, 0, 0.5f, 10, 0, 0)
                    .rot("right_ear", 0, 10, 0, 0, 0.15f, -10, 0, 0, 0.5f, 10, 0, 0)
                    .rot("scarf_tail", 0, 10, 0, 0, 0.15f, 35, 0, 0, 0.5f, 10, 0, 0)
                    .build(),
            2.5f, 50f, "head",
            A.of(3f).loop()
                    .rot("left_ear", 0, 0, 0, 0, 1.2f, 0, 0, 0, 1.35f, 0, 0, 15, 1.5f, 0, 0, 0, 3f, 0, 0, 0)
                    .rot("right_ear", 0, 0, 0, 0, 2.0f, 0, 0, 0, 2.15f, 0, 0, -15, 2.3f, 0, 0, 0, 3f, 0, 0, 0)
                    .rot("tail", 0, 0, 0, 0, 1.5f, 0, 15, 0, 3f, 0, 0, 0)
                    .rot("scarf_tail", 0, 0, 0, 0, 1.5f, 8, 0, -6, 3f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.5f, 0, 0, 6, 1f, 0, 0, 0, 3f, 0, 0, 0)
                    .build(),
            // 1: a quick chopper slash
            A.of(0.45f)
                    .rot("right_arm", 0, 0, 0, 0, 0.12f, -110, 0, 0, 0.25f, 70, 0, 0, 0.45f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.12f, 0, 20, 0, 0.25f, 10, -15, 0, 0.45f, 0, 0, 0)
                    .pos("body", 0, 0, 0, 0, 0.12f, 0, 1, 0, 0.25f, 0, 0, -2, 0.45f, 0, 0, 0)
                    .build());

    public static final AnimSet EMUL = new AnimSet(
            A.of(0.6f).loop()
                    .rot("left_leg", 0, 30, 0, 0, 0.3f, -30, 0, 0, 0.6f, 30, 0, 0)
                    .rot("right_leg", 0, -30, 0, 0, 0.3f, 30, 0, 0, 0.6f, -30, 0, 0)
                    .rot("left_arm", 0, -20, 0, 0, 0.3f, 20, 0, 0, 0.6f, -20, 0, 0)
                    .rot("right_arm", 0, 20, 0, 0, 0.3f, -20, 0, 0, 0.6f, 20, 0, 0)
                    .pos("body", 0, 0, 0, 0, 0.15f, 0, 0.6f, 0, 0.3f, 0, 0, 0, 0.45f, 0, 0.6f, 0, 0.6f, 0, 0, 0)
                    .rot("left_ear", 0, 0, 0, 0, 0.15f, 0, 0, -8, 0.45f, 0, 0, 6, 0.6f, 0, 0, 0)
                    .rot("right_ear", 0, 0, 0, 0, 0.15f, 0, 0, 8, 0.45f, 0, 0, -6, 0.6f, 0, 0, 0)
                    .rot("coat_tails", 0, 0, 0, 0, 0.15f, 10, 0, 0, 0.45f, 10, 0, 0, 0.6f, 0, 0, 0)
                    .build(),
            3f, 60f, "head",
            A.of(4f).loop()
                    .rot("left_ear", 0, 0, 0, 0, 1f, 0, 0, -5, 1.4f, 0, 0, 3, 1.8f, 0, 0, 0, 4f, 0, 0, 0)
                    .rot("right_ear", 0, 0, 0, 0, 2.6f, 0, 0, 5, 3f, 0, 0, -3, 3.4f, 0, 0, 0, 4f, 0, 0, 0)
                    .rot("hat", 0, 0, 0, 0, 2f, 0, 0, -4, 4f, 0, 0, 0)
                    .pos("head", 0, 0, 0, 0, 2f, 0, -0.3f, 0, 4f, 0, 0, 0)
                    .rot("tail", 0, 0, -10, 0, 2f, 0, 10, 0, 4f, 0, -10, 0)
                    .build(),
            // 1: an excited wave
            A.of(1f)
                    .rot("right_arm", 0, 0, 0, 0, 0.2f, -150, 0, 20, 0.4f, -150, 0, -10, 0.6f, -150, 0, 20, 0.8f, -150, 0, -10, 1f, 0, 0, 0)
                    .rot("left_ear", 0, 0, 0, 0, 0.3f, 0, 0, -20, 0.7f, 0, 0, -20, 1f, 0, 0, 0)
                    .rot("right_ear", 0, 0, 0, 0, 0.3f, 0, 0, 20, 0.7f, 0, 0, 20, 1f, 0, 0, 0)
                    .build());

    public static final AnimSet VYSACHE = new AnimSet(
            A.of(1.2f).loop()
                    .rot("left_leg", 0, 20, 0, 0, 0.6f, -20, 0, 0, 1.2f, 20, 0, 0)
                    .rot("right_leg", 0, -20, 0, 0, 0.6f, 20, 0, 0, 1.2f, -20, 0, 0)
                    .rot("left_arm", 0, -12, 0, 0, 0.6f, 12, 0, 0, 1.2f, -12, 0, 0)
                    .rot("right_arm", 0, 12, 0, 0, 0.6f, -12, 0, 0, 1.2f, 12, 0, 0)
                    .rot("cape", 0, 6, 0, 0, 0.6f, 12, 0, 0, 1.2f, 6, 0, 0)
                    .build(),
            1.5f, 40f, "head",
            A.of(5f).loop()
                    .rot("head", 0, 0, 0, 0, 1f, -6, 0, 0, 1.6f, -6, 0, 0, 2.2f, 0, 0, 0, 5f, 0, 0, 0)
                    .rot("pipe", 0, 0, 0, 0, 1f, -8, 0, 0, 1.6f, -8, 0, 0, 2.2f, 0, 0, 0, 5f, 0, 0, 0)
                    .rot("cape", 0, 0, 0, 0, 2.5f, 4, 0, 2, 5f, 0, 0, 0)
                    .rot("right_ear", 0, 0, 0, 0, 3.5f, 0, 0, 0, 3.65f, 0, 0, -10, 3.8f, 0, 0, 0, 5f, 0, 0, 0)
                    .pos("body", 0, 0, 0, 0, 2.5f, 0, -0.4f, 0, 5f, 0, 0, 0)
                    .build());

    public static final AnimSet BILAC = new AnimSet(
            A.of(0.8f).loop()
                    .rot("left_leg", 0, 25, 0, 0, 0.4f, -25, 0, 0, 0.8f, 25, 0, 0)
                    .rot("right_leg", 0, -25, 0, 0, 0.4f, 25, 0, 0, 0.8f, -25, 0, 0)
                    .rot("left_arm", 0, -20, 0, 0, 0.4f, 20, 0, 0, 0.8f, -20, 0, 0)
                    .rot("right_arm", 0, 8, 0, 0, 0.4f, -8, 0, 0, 0.8f, 8, 0, 0)
                    .pos("body", 0, 0, 0, 0, 0.2f, 0, 0.5f, 0, 0.4f, 0, 0, 0, 0.6f, 0, 0.5f, 0, 0.8f, 0, 0, 0)
                    .build(),
            3f, 60f, "head",
            A.of(3f).loop()
                    .rot("right_arm", 0, 0, 0, 0, 0.4f, -25, 0, 0, 0.55f, 0, 0, 0, 0.9f, -25, 0, 0, 1.05f, 0, 0, 0, 3f, 0, 0, 0)
                    .rot("left_ear", 0, 0, 0, 0, 2f, 0, 0, 0, 2.15f, 0, 0, 10, 2.3f, 0, 0, 0, 3f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.4f, 6, 0, 0, 0.55f, 0, 0, 0, 0.9f, 6, 0, 0, 1.05f, 0, 0, 0, 3f, 0, 0, 0)
                    .build(),
            // 1: a full hammer strike
            A.of(0.8f)
                    .rot("right_arm", 0, 0, 0, 0, 0.3f, -165, 0, 0, 0.45f, 15, 0, 0, 0.8f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.3f, -8, 0, 0, 0.45f, 12, 0, 0, 0.8f, 0, 0, 0)
                    .build());

    // ==== Humanoid NPCs and allies =====================================================

    private static final AnimationDefinition BIPED_WALK = A.of(1f).loop()
            .rot("left_leg", 0, 30, 0, 0, 0.5f, -30, 0, 0, 1f, 30, 0, 0)
            .rot("right_leg", 0, -30, 0, 0, 0.5f, 30, 0, 0, 1f, -30, 0, 0)
            .rot("left_arm", 0, -25, 0, 0, 0.5f, 25, 0, 0, 1f, -25, 0, 0)
            .rot("right_arm", 0, 25, 0, 0, 0.5f, -25, 0, 0, 1f, 25, 0, 0)
            .rot("ponytail", 0, 6, 0, 0, 0.5f, 12, 0, 0, 1f, 6, 0, 0)
            .rot("braid", 0, 0, 0, 0, 0.5f, 6, 0, 0, 1f, 0, 0, 0)
            .rot("left_tail", 0, 4, 0, 0, 0.5f, 10, 0, 0, 1f, 4, 0, 0)
            .rot("right_tail", 0, 4, 0, 0, 0.5f, 10, 0, 0, 1f, 4, 0, 0)
            .build();

    private static final AnimationDefinition BIPED_IDLE = A.of(4f).loop()
            .pos("body", 0, 0, 0, 0, 2f, 0, -0.3f, 0, 4f, 0, 0, 0)
            .rot("left_arm", 0, 0, 0, -2, 2f, 0, 0, -4, 4f, 0, 0, -2)
            .rot("right_arm", 0, 0, 0, 2, 2f, 0, 0, 4, 4f, 0, 0, 2)
            .rot("ponytail", 0, 0, 0, -3, 2f, 0, 0, 3, 4f, 0, 0, -3)
            .rot("braid", 0, 0, 0, 0, 2f, 3, 0, 0, 4f, 0, 0, 0)
            .rot("left_tail", 0, 0, 0, 0, 2f, 0, 0, -4, 4f, 0, 0, 0)
            .rot("right_tail", 0, 0, 0, 0, 2f, 0, 0, 4, 4f, 0, 0, 0)
            .build();

    private static final AnimationDefinition BIPED_GESTURE = A.of(1f)
            .rot("right_arm", 0, 0, 0, 0, 0.25f, -110, 0, 15, 0.75f, -105, 0, 10, 1f, 0, 0, 0)
            .rot("head", 0, 0, 0, 0, 0.25f, 6, 0, 0, 1f, 0, 0, 0)
            .build();

    public static final AnimSet NPC = new AnimSet(BIPED_WALK, 1f, 40f, "head", BIPED_IDLE, BIPED_GESTURE);

    /** Setsuna drifts a little above the grass. */
    public static final AnimSet GHOST = new AnimSet(BIPED_WALK, 0.6f, 20f, "head",
            A.of(4f).loop()
                    .pos("root", 0, 0, 1, 0, 2f, 0, 2, 0, 4f, 0, 1, 0)
                    .rot("left_arm", 0, 0, 0, -3, 2f, 0, 0, -6, 4f, 0, 0, -3)
                    .rot("right_arm", 0, 0, 0, 3, 2f, 0, 0, 6, 4f, 0, 0, 3)
                    .rot("braid", 0, 0, 0, 0, 2f, 5, 0, 3, 4f, 0, 0, 0)
                    .rot("coat_skirt", 0, 0, 0, 0, 2f, 4, 0, 0, 4f, 0, 0, 0)
                    .build(),
            BIPED_GESTURE);

    /** Pencilgon: spear thrust (1) and a spinning sweep (2). */
    public static final AnimSet PENCILGON = new AnimSet(BIPED_WALK, 1f, 40f, "head", BIPED_IDLE,
            A.of(0.7f)
                    .rot("right_arm", 0, 0, 0, 0, 0.2f, -60, 30, 0, 0.35f, -95, -5, 0, 0.5f, -95, -5, 0, 0.7f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.2f, 0, 25, 0, 0.35f, 8, -10, 0, 0.7f, 0, 0, 0)
                    .pos("root", 0, 0, 0, 0, 0.35f, 0, 0, -3, 0.7f, 0, 0, 0)
                    .build(),
            A.of(1.0f)
                    .rot("root", 0, 0, 0, 0, 0.2f, 0, 30, 0, 0.6f, 0, -330, 0, 0.7f, 0, -360, 0, 1.0f, 0, -360, 0)
                    .rot("right_arm", 0, 0, 0, 0, 0.2f, -80, 0, -60, 0.7f, -80, 0, -60, 1.0f, 0, 0, 0)
                    .rot("ponytail", 0.2f, 0, 0, 0, 0.5f, 40, 0, 0, 1.0f, 0, 0, 0)
                    .build());

    /** Oikatzo: a one-two punch combo (1) and a rising uppercut (2). */
    public static final AnimSet OIKATZO = new AnimSet(BIPED_WALK, 1.2f, 45f, "head", BIPED_IDLE,
            A.of(0.6f)
                    .rot("left_arm", 0, 0, 0, 0, 0.1f, -90, -10, 0, 0.22f, 0, 0, 0, 0.6f, 0, 0, 0)
                    .rot("right_arm", 0, 0, 0, 0, 0.22f, 0, 0, 0, 0.32f, -90, 10, 0, 0.45f, 0, 0, 0, 0.6f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.1f, 0, 20, 0, 0.32f, 0, -20, 0, 0.6f, 0, 0, 0)
                    .build(),
            A.of(0.9f)
                    .pos("root", 0, 0, 0, 0, 0.25f, 0, -2, 0, 0.45f, 0, 3, 0, 0.9f, 0, 0, 0)
                    .rot("right_arm", 0, 0, 0, 0, 0.25f, 20, 0, 0, 0.45f, -170, 0, 0, 0.7f, -160, 0, 0, 0.9f, 0, 0, 0)
                    .rot("left_tail", 0.3f, 0, 0, 0, 0.5f, 30, 0, 0, 0.9f, 0, 0, 0)
                    .rot("right_tail", 0.3f, 0, 0, 0, 0.5f, 30, 0, 0, 0.9f, 0, 0, 0)
                    .build());

    // ==== Goblins ======================================================================

    public static final AnimSet GOBLIN = new AnimSet(
            A.of(0.6f).loop()
                    .rot("left_leg", 0, 35, 0, 0, 0.3f, -35, 0, 0, 0.6f, 35, 0, 0)
                    .rot("right_leg", 0, -35, 0, 0, 0.3f, 35, 0, 0, 0.6f, -35, 0, 0)
                    .rot("left_arm", 0, -30, 0, 0, 0.3f, 30, 0, 0, 0.6f, -30, 0, 0)
                    .rot("right_arm", 0, 20, 0, 0, 0.3f, -20, 0, 0, 0.6f, 20, 0, 0)
                    .rot("body", 0, 0, 0, -4, 0.3f, 0, 0, 4, 0.6f, 0, 0, -4)
                    .rot("left_ear", 0, 0, 0, 0, 0.15f, 0, -8, 0, 0.45f, 0, 8, 0, 0.6f, 0, 0, 0)
                    .rot("right_ear", 0, 0, 0, 0, 0.15f, 0, 8, 0, 0.45f, 0, -8, 0, 0.6f, 0, 0, 0)
                    .build(),
            2.5f, 50f, "head",
            A.of(2.5f).loop()
                    .rot("left_ear", 0, 0, 0, 0, 1.0f, 0, 0, 0, 1.1f, 0, 0, 12, 1.2f, 0, 0, 0, 2.5f, 0, 0, 0)
                    .rot("right_ear", 0, 0, 0, 0, 1.8f, 0, 0, 0, 1.9f, 0, 0, -12, 2.0f, 0, 0, 0, 2.5f, 0, 0, 0)
                    .pos("body", 0, 0, 0, 0, 1.25f, 0, -0.4f, 0, 2.5f, 0, 0, 0)
                    .rot("right_arm", 0, -5, 0, 0, 1.25f, -12, 0, 0, 2.5f, -5, 0, 0)
                    .build(),
            // 1: overhead axe chop
            A.of(0.5f)
                    .rot("right_arm", 0, 0, 0, 0, 0.15f, -150, 0, 0, 0.3f, -15, 0, 0, 0.5f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.15f, -8, 0, 0, 0.3f, 15, 0, 0, 0.5f, 0, 0, 0)
                    .build());

    // ==== Empire Bees ==================================================================

    private static final AnimationDefinition BEE_IDLE = A.of(0.2f).loop()
            .rot("left_wing", 0, 0, 0, -25, 0.1f, 0, 0, 25, 0.2f, 0, 0, -25)
            .rot("right_wing", 0, 0, 0, 25, 0.1f, 0, 0, -25, 0.2f, 0, 0, 25)
            .pos("body", 0, 0, 0, 0, 0.1f, 0, 0.4f, 0, 0.2f, 0, 0, 0)
            .rot("abdomen", 0, 0, 0, 0, 0.1f, 3, 0, 0, 0.2f, 0, 0, 0)
            .build();

    public static final AnimSet EMPIRE_BEE = new AnimSet(null, 1f, 1f, "head",
            BEE_IDLE,
            // 1: sting dive (the abdomen curls under)
            A.of(1.3f)
                    .rot("body", 0, 0, 0, 0, 0.4f, -20, 0, 0, 0.6f, 35, 0, 0, 1.0f, 30, 0, 0, 1.3f, 0, 0, 0)
                    .rot("abdomen", 0, 0, 0, 0, 0.4f, -10, 0, 0, 0.6f, 45, 0, 0, 1.0f, 40, 0, 0, 1.3f, 0, 0, 0)
                    .build(),
            // 2: summoning buzz
            A.of(1.5f)
                    .rot("body", 0, 0, 0, 0, 0.3f, 0, 0, 10, 0.5f, 0, 0, -10, 0.7f, 0, 0, 10, 0.9f, 0, 0, -10, 1.5f, 0, 0, 0)
                    .rot("left_antenna", 0, 0, 0, 0, 0.3f, 20, 0, 15, 1.2f, 20, 0, 15, 1.5f, 0, 0, 0)
                    .rot("right_antenna", 0, 0, 0, 0, 0.3f, 20, 0, -15, 1.2f, 20, 0, -15, 1.5f, 0, 0, 0)
                    .build(),
            // 3: stinger barrage (aims the abdomen forward)
            A.of(1.7f)
                    .rot("body", 0, 0, 0, 0, 0.4f, -35, 0, 0, 1.4f, -35, 0, 0, 1.7f, 0, 0, 0)
                    .rot("abdomen", 0, 0, 0, 0, 0.4f, 70, 0, 0, 1.4f, 70, 0, 0, 1.7f, 0, 0, 0)
                    .build());

    // ==== Quad Beetle ==================================================================

    public static final AnimSet QUAD_BEETLE = new AnimSet(
            A.of(0.6f).loop()
                    .rot("leg_l0", 0, 0, 25, 0, 0.3f, 0, -25, 0, 0.6f, 0, 25, 0)
                    .rot("leg_r1", 0, 0, 25, 0, 0.3f, 0, -25, 0, 0.6f, 0, 25, 0)
                    .rot("leg_l2", 0, 0, 25, 0, 0.3f, 0, -25, 0, 0.6f, 0, 25, 0)
                    .rot("leg_r0", 0, 0, -25, 0, 0.3f, 0, 25, 0, 0.6f, 0, -25, 0)
                    .rot("leg_l1", 0, 0, -25, 0, 0.3f, 0, 25, 0, 0.6f, 0, -25, 0)
                    .rot("leg_r2", 0, 0, -25, 0, 0.3f, 0, 25, 0, 0.6f, 0, -25, 0)
                    .pos("body", 0, 0, 0, 0, 0.15f, 0, 0.5f, 0, 0.3f, 0, 0, 0, 0.45f, 0, 0.5f, 0, 0.6f, 0, 0, 0)
                    .build(),
            2f, 40f, "head",
            A.of(3f).loop()
                    .rot("head", 0, 0, 0, 0, 1.5f, 4, 0, 0, 3f, 0, 0, 0)
                    .rot("tusk_l", 0, 0, 0, 0, 0.4f, 0, 12, 0, 0.8f, 0, 0, 0, 3f, 0, 0, 0)
                    .rot("tusk_r", 0, 0, 0, 0, 0.4f, 0, -12, 0, 0.8f, 0, 0, 0, 3f, 0, 0, 0)
                    .build(),
            // 1: horn toss
            A.of(0.6f)
                    .rot("head", 0, 0, 0, 0, 0.15f, 18, 0, 0, 0.3f, -32, 0, 0, 0.6f, 0, 0, 0)
                    .rot("tusk_l", 0, 0, 0, 0, 0.15f, 0, 25, 0, 0.3f, 0, -10, 0, 0.6f, 0, 0, 0)
                    .rot("tusk_r", 0, 0, 0, 0, 0.15f, 0, -25, 0, 0.3f, 0, 10, 0, 0.6f, 0, 0, 0)
                    .build(),
            // 2: horns down, then a drilling, spinning charge
            A.of(2.0f)
                    .rot("head", 0, 0, 0, 0, 0.3f, 20, 0, 0, 1.8f, 20, 0, 0, 2.0f, 0, 0, 0)
                    .pos("body", 0, 0, 0, 0, 0.3f, 0, -1, 0, 0.75f, 0, -1, 0, 0.85f, 0, 3, 0, 1.75f, 0, 3, 0, 2.0f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.75f, 0, 0, 0, 1.0f, 0, 0, 270, 1.25f, 0, 0, 540, 1.5f, 0, 0, 810,
                            1.75f, 0, 0, 1080, 2.0f, 0, 0, 1080)
                    .build());

    // ==== Mud Digger ===================================================================

    public static final AnimSet MUD_DIGGER = new AnimSet(
            A.of(0.8f).loop()
                    .rot("claw_fl", 0, 30, 0, 0, 0.4f, -30, 0, 0, 0.8f, 30, 0, 0)
                    .rot("claw_fr", 0, -30, 0, 0, 0.4f, 30, 0, 0, 0.8f, -30, 0, 0)
                    .rot("leg_bl", 0, -25, 0, 0, 0.4f, 25, 0, 0, 0.8f, -25, 0, 0)
                    .rot("leg_br", 0, 25, 0, 0, 0.4f, -25, 0, 0, 0.8f, 25, 0, 0)
                    .rot("body", 0, 0, 0, -3, 0.4f, 0, 0, 3, 0.8f, 0, 0, -3)
                    .rot("tail", 0, 0, -10, 0, 0.4f, 0, 10, 0, 0.8f, 0, -10, 0)
                    .rot("tail2", 0, 0, -6, 0, 0.2f, 0, -12, 0, 0.6f, 0, 12, 0, 0.8f, 0, -6, 0)
                    .build(),
            1.6f, 40f, "head",
            A.of(3f).loop()
                    .rot("jaw", 0, 0, 0, 0, 1.5f, 7, 0, 0, 3f, 0, 0, 0)
                    .rot("tail", 0, 0, -8, 0, 1.5f, 0, 8, 0, 3f, 0, -8, 0)
                    .rot("tail_fin", 0, 0, -10, 0, 1.5f, 0, 10, 0, 3f, 0, -10, 0)
                    .rot("whisker_l", 0, 0, 0, 0, 1.5f, 0, 0, 10, 3f, 0, 0, 0)
                    .rot("whisker_r", 0, 0, 0, 0, 1.5f, 0, 0, -10, 3f, 0, 0, 0)
                    .build(),
            // 1: bite
            A.of(0.8f)
                    .rot("jaw", 0, 0, 0, 0, 0.3f, 45, 0, 0, 0.45f, 0, 0, 0, 0.8f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.3f, -15, 0, 0, 0.45f, 10, 0, 0, 0.8f, 0, 0, 0)
                    .pos("body", 0, 0, 0, 0, 0.3f, 0, 0, 1.5f, 0.45f, 0, 0, -3, 0.8f, 0, 0, 0)
                    .build(),
            // 2: burrow and burst out underneath
            A.of(3.2f)
                    .pos("root", 0, 0, 0, 0, 0.3f, 0, -24, 0, 2.45f, 0, -24, 0, 2.55f, 0, 4, 0, 2.8f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.3f, 30, 0, 0, 2.45f, 30, 0, 0, 2.55f, -30, 0, 0, 3.2f, 0, 0, 0)
                    .rot("jaw", 2.4f, 0, 0, 0, 2.55f, 50, 0, 0, 3.2f, 0, 0, 0)
                    .build(),
            // 3: tail spin
            A.of(1.1f)
                    .rot("root", 0, 0, 0, 0, 0.4f, 0, -40, 0, 0.6f, 0, 80, 0, 1.1f, 0, 0, 0)
                    .rot("tail", 0, 0, 0, 0, 0.4f, 0, 30, 0, 0.6f, 0, -40, 0, 1.1f, 0, 0, 0)
                    .rot("tail2", 0, 0, 0, 0, 0.4f, 0, 20, 0, 0.65f, 0, -30, 0, 1.1f, 0, 0, 0)
                    .build(),
            // 4: the desperate quake (dives, shakes the swamp, launches from below)
            A.of(4.0f)
                    .pos("root", 0, 0, 0, 0, 0.4f, 0, -24, 0, 3.4f, 0, -24, 0, 3.55f, 0, 8, 0, 4.0f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.4f, 35, 0, 0, 3.4f, 35, 0, 0, 3.55f, -40, 0, 0, 4.0f, 0, 0, 0)
                    .rot("jaw", 3.3f, 0, 0, 0, 3.55f, 55, 0, 0, 4.0f, 0, 0, 0)
                    .build());

    // ==== Ravenous Snake ===============================================================

    private static A slither(A a, float length, float amplitude, int fromSegment) {
        for (int i = fromSegment; i < 10; i++) {
            a.rot("seg" + i, wave(length, amplitude * (0.6f + i * 0.06f), i * 0.7f, 1, 8));
        }
        return a;
    }

    public static final AnimSet RAVENOUS_SNAKE = new AnimSet(
            slither(A.of(1.2f).loop(), 1.2f, 14f, 0)
                    .rot("head", wave(1.2f, 8f, -0.7f, 1, 8))
                    .build(),
            1.0f, 30f, null,
            slither(A.of(2.4f).loop(), 2.4f, 5f, 0)
                    .pos("tongue", 0, 0, 0, 0, 0.9f, 0, 0, 0, 1.0f, 0, 0, -3, 1.1f, 0, 0, 0, 1.2f, 0, 0, -3, 1.3f, 0, 0, 0, 2.4f, 0, 0, 0)
                    .rot("hair", 0, 0, 0, 0, 1.2f, 5, 0, 0, 2.4f, 0, 0, 0)
                    .rot("hair2", 0, 0, 0, 0, 1.2f, 0, 0, 0, 1.8f, 6, 0, 0, 2.4f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 1.2f, -6, 0, 0, 2.4f, 0, 0, 0)
                    .build(),
            // 1: rearing lunge bite
            A.of(0.8f)
                    .rot("head", 0, 0, 0, 0, 0.3f, -28, 0, 0, 0.45f, 8, 0, 0, 0.8f, 0, 0, 0)
                    .pos("head", 0, 0, 0, 0, 0.3f, 0, 3, 4, 0.45f, 0, 0, -10, 0.8f, 0, 0, 0)
                    .rot("jaw", 0, 0, 0, 0, 0.3f, 50, 0, 0, 0.45f, 0, 0, 0, 0.8f, 0, 0, 0)
                    .rot("seg0", 0, 0, 0, 0, 0.3f, 25, 0, 0, 0.45f, -10, 0, 0, 0.8f, 0, 0, 0)
                    .build(),
            // 2: tail sweep
            A.of(1.2f)
                    .rot("seg5", 0, 0, 0, 0, 0.4f, 0, 25, 0, 0.6f, 0, -35, 0, 1.2f, 0, 0, 0)
                    .rot("seg6", 0, 0, 0, 0, 0.4f, 0, 25, 0, 0.62f, 0, -35, 0, 1.2f, 0, 0, 0)
                    .rot("seg7", 0, 0, 0, 0, 0.4f, 0, 25, 0, 0.64f, 0, -35, 0, 1.2f, 0, 0, 0)
                    .rot("seg8", 0, 0, 0, 0, 0.4f, 0, 25, 0, 0.66f, 0, -35, 0, 1.2f, 0, 0, 0)
                    .rot("seg9", 0, 0, 0, 0, 0.4f, 0, 25, 0, 0.68f, 0, -35, 0, 1.2f, 0, 0, 0)
                    .build(),
            // 3: poison bloom (the tail hair opens like a flower)
            A.of(1.8f)
                    .rot("seg7", 0, 0, 0, 0, 0.4f, 20, 0, 0, 1.5f, 20, 0, 0, 1.8f, 0, 0, 0)
                    .rot("seg8", 0, 0, 0, 0, 0.4f, 25, 0, 0, 1.5f, 25, 0, 0, 1.8f, 0, 0, 0)
                    .rot("seg9", 0, 0, 0, 0, 0.4f, 25, 0, 0, 1.5f, 25, 0, 0, 1.8f, 0, 0, 0)
                    .rot("tail_hair", 0, 0, 0, 0, 0.5f, 50, 0, 0, 1.5f, 50, 0, 0, 1.8f, 0, 0, 0)
                    .rot("tail_hair_l", 0, 0, 0, 0, 0.5f, 25, 45, 0, 1.5f, 25, 45, 0, 1.8f, 0, 0, 0)
                    .rot("tail_hair_r", 0, 0, 0, 0, 0.5f, 25, -45, 0, 1.5f, 25, -45, 0, 1.8f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.4f, -12, 0, 0, 1.5f, -12, 0, 0, 1.8f, 0, 0, 0)
                    .build(),
            // 4: slithering charge
            slither(A.of(1.0f), 1.0f, 22f, 0)
                    .rot("head", 0, 0, 0, 0, 0.2f, 10, 0, 0, 0.8f, 10, 0, 0, 1.0f, 0, 0, 0)
                    .build());

    // ==== Aberrant Woodmage ============================================================

    public static final AnimSet WOODMAGE = new AnimSet(
            A.of(1.4f).loop()
                    .rot("left_leg", 0, 18, 0, 0, 0.7f, -18, 0, 0, 1.4f, 18, 0, 0)
                    .rot("right_leg", 0, -18, 0, 0, 0.7f, 18, 0, 0, 1.4f, -18, 0, 0)
                    .rot("left_arm", 0, -12, 0, 0, 0.7f, 12, 0, 0, 1.4f, -12, 0, 0)
                    .rot("chain_fl", 0, 8, 0, 0, 0.7f, -8, 0, 0, 1.4f, 8, 0, 0)
                    .rot("chain_fr", 0, -8, 0, 0, 0.7f, 8, 0, 0, 1.4f, -8, 0, 0)
                    .rot("chain_bl", 0, 10, 0, 0, 0.7f, -6, 0, 0, 1.4f, 10, 0, 0)
                    .rot("chain_br", 0, -6, 0, 0, 0.7f, 10, 0, 0, 1.4f, -6, 0, 0)
                    .rot("hat2", 0, 0, 0, 0, 0.7f, -6, 0, 0, 1.4f, 0, 0, 0)
                    .build(),
            1.2f, 35f, "head",
            A.of(4f).loop()
                    .rot("chain_fl", 0, 0, 0, 0, 1f, 12, 0, 8, 2f, -6, 0, -4, 3f, 8, 0, 10, 4f, 0, 0, 0)
                    .rot("chain_fr", 0, 0, 0, 0, 1.3f, -10, 0, -8, 2.3f, 8, 0, 6, 3.3f, -6, 0, -10, 4f, 0, 0, 0)
                    .rot("chain_bl", 0, 0, 0, 0, 0.8f, 10, 0, -6, 1.8f, -10, 0, 8, 2.8f, 6, 0, -4, 4f, 0, 0, 0)
                    .rot("chain_br", 0, 0, 0, 0, 1.6f, -8, 0, 8, 2.6f, 10, 0, -8, 3.6f, -4, 0, 4, 4f, 0, 0, 0)
                    .rot("hat2", 0, 0, 0, 0, 2f, -5, 0, 4, 4f, 0, 0, 0)
                    .rot("hat3", 0, 0, 0, 0, 2f, -6, 0, 6, 4f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 2f, 0, 0, 6, 4f, 0, 0, 0)
                    .pos("body", 0, 0, 0, 0, 2f, 0, -0.4f, 0, 4f, 0, 0, 0)
                    .build(),
            // 1: root spears (raises the staff, then drives it down)
            A.of(1.8f)
                    .rot("right_arm", 0, 0, 0, 0, 0.5f, -120, 0, 0, 1.2f, -125, 0, 0, 1.35f, 20, 0, 0, 1.8f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.5f, -8, 0, 0, 1.2f, -8, 0, 0, 1.35f, 12, 0, 0, 1.8f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.5f, -15, 0, 0, 1.2f, -15, 0, 0, 1.35f, 10, 0, 0, 1.8f, 0, 0, 0)
                    .build(),
            // 2: chain lash
            A.of(1.2f)
                    .rot("chain_fl", 0, 0, 0, 0, 0.3f, 40, 0, 0, 0.45f, -110, 0, 0, 0.8f, -100, 0, 0, 1.2f, 0, 0, 0)
                    .rot("chain_fr", 0, 0, 0, 0, 0.3f, 40, 0, 0, 0.5f, -110, 0, 0, 0.85f, -100, 0, 0, 1.2f, 0, 0, 0)
                    .rot("chain_bl", 0, 0, 0, 0, 0.35f, 30, 0, -40, 0.55f, -60, 0, -40, 0.9f, -50, 0, -30, 1.2f, 0, 0, 0)
                    .rot("chain_br", 0, 0, 0, 0, 0.35f, 30, 0, 40, 0.55f, -60, 0, 40, 0.9f, -50, 0, 30, 1.2f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.3f, -6, 0, 0, 0.5f, 8, 0, 0, 1.2f, 0, 0, 0)
                    .build(),
            // 3: magic bolt from the staff
            A.of(1.0f)
                    .rot("right_arm", 0, 0, 0, 0, 0.3f, -60, -20, 0, 0.7f, -60, -20, 0, 1.0f, 0, 0, 0)
                    .rot("staff", 0, 0, 0, 0, 0.3f, -60, 0, 0, 0.7f, -60, 0, 0, 1.0f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.3f, 0, -15, 0, 0.7f, 0, -15, 0, 1.0f, 0, 0, 0)
                    .build(),
            // 4: grasp (reaches out while the front chains seize the target)
            A.of(1.6f)
                    .rot("left_arm", 0, 0, 0, 0, 0.4f, -90, 0, 0, 1.2f, -90, 0, 0, 1.6f, 0, 0, 0)
                    .rot("chain_fl", 0, 0, 0, 0, 0.4f, -85, 0, 0, 1.0f, -85, 0, 0, 1.2f, -30, 0, 0, 1.6f, 0, 0, 0)
                    .rot("chain_fr", 0, 0, 0, 0, 0.4f, -85, 0, 0, 1.0f, -85, 0, 0, 1.2f, -30, 0, 0, 1.6f, 0, 0, 0)
                    .scale("chain_fl", 0, 1, 1, 1, 0.4f, 1, 1.8f, 1, 1.0f, 1, 1.8f, 1, 1.2f, 1, 1, 1)
                    .scale("chain_fr", 0, 1, 1, 1, 0.4f, 1, 1.8f, 1, 1.0f, 1, 1.8f, 1, 1.2f, 1, 1, 1)
                    .build());
}
