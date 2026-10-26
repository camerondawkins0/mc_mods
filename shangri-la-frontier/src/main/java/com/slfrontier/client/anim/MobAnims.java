package com.slfrontier.client.anim;

import net.minecraft.client.animation.AnimationDefinition;

/**
 * Keyframe animations for every creature. Times are in seconds and rotations in
 * degrees. Slot numbers match the attack ids each entity sends (attack id + 1).
 */
public final class MobAnims {
    private MobAnims() {
    }

    // ---- Vorpal Bunny ---------------------------------------------------------------
    public static final AnimSet VORPAL_BUNNY = new AnimSet(
            A.of(0.5f).loop()
                    .pos("body", 0, 0, 0, 0, 0.25f, 0, 1.5f, 0, 0.5f, 0, 0, 0)
                    .rot("front_left_leg", 0, 40, 0, 0, 0.25f, -40, 0, 0, 0.5f, 40, 0, 0)
                    .rot("front_right_leg", 0, 40, 0, 0, 0.25f, -40, 0, 0, 0.5f, 40, 0, 0)
                    .rot("back_left_leg", 0, -40, 0, 0, 0.25f, 40, 0, 0, 0.5f, -40, 0, 0)
                    .rot("back_right_leg", 0, -40, 0, 0, 0.25f, 40, 0, 0, 0.5f, -40, 0, 0)
                    .rot("left_ear", 0, 10, 0, 0, 0.25f, -5, 0, 0, 0.5f, 10, 0, 0)
                    .rot("right_ear", 0, 10, 0, 0, 0.25f, -5, 0, 0, 0.5f, 10, 0, 0)
                    .build(),
            2.5f, 50f, "head",
            A.of(3f).loop()
                    .rot("left_ear", 0, 0, 0, 0, 1.2f, 0, 0, 0, 1.35f, 0, 0, -15, 1.5f, 0, 0, 0, 3f, 0, 0, 0)
                    .rot("right_ear", 0, 0, 0, 0, 2.0f, 0, 0, 0, 2.15f, 0, 0, 15, 2.3f, 0, 0, 0, 3f, 0, 0, 0)
                    .rot("tail", 0, 0, 0, 0, 1.5f, 0, 15, 0, 3f, 0, 0, 0)
                    .build(),
            A.of(0.4f)
                    .rot("head", 0, 0, 0, 0, 0.15f, 30, 0, 0, 0.4f, 0, 0, 0)
                    .pos("body", 0, 0, 0, 0, 0.15f, 0, 0, -2, 0.4f, 0, 0, 0)
                    .build());

    // ---- Rabbitfolk NPCs (Emul, Bilac, Vysache) ---------------------------------------
    public static final AnimSet RABBITFOLK = new AnimSet(
            A.of(0.8f).loop()
                    .rot("left_leg", 0, 30, 0, 0, 0.4f, -30, 0, 0, 0.8f, 30, 0, 0)
                    .rot("right_leg", 0, -30, 0, 0, 0.4f, 30, 0, 0, 0.8f, -30, 0, 0)
                    .rot("left_arm", 0, -25, 0, 0, 0.4f, 25, 0, 0, 0.8f, -25, 0, 0)
                    .rot("right_arm", 0, 25, 0, 0, 0.4f, -25, 0, 0, 0.8f, 25, 0, 0)
                    .pos("body", 0, 0, 0, 0, 0.2f, 0, 0.5f, 0, 0.4f, 0, 0, 0, 0.6f, 0, 0.5f, 0, 0.8f, 0, 0, 0)
                    .build(),
            3f, 60f, "head",
            A.of(4f).loop()
                    .rot("left_ear", 0, 0, 0, 0, 1f, 0, 0, -6, 1.2f, 0, 0, 4, 1.4f, 0, 0, 0, 4f, 0, 0, 0)
                    .rot("right_ear", 0, 0, 0, 0, 2.6f, 0, 0, 6, 2.8f, 0, 0, -4, 3f, 0, 0, 0, 4f, 0, 0, 0)
                    .pos("head", 0, 0, 0, 0, 2f, 0, -0.3f, 0, 4f, 0, 0, 0)
                    .rot("tail", 0, 0, -10, 0, 2f, 0, 10, 0, 4f, 0, -10, 0)
                    .build());

    // ---- Empire Bee / Queen ------------------------------------------------------------
    private static final AnimationDefinition BEE_IDLE = A.of(0.2f).loop()
            .rot("left_wing", 0, 0, 0, -25, 0.1f, 0, 0, 25, 0.2f, 0, 0, -25)
            .rot("right_wing", 0, 0, 0, 25, 0.1f, 0, 0, -25, 0.2f, 0, 0, 25)
            .pos("body", 0, 0, 0, 0, 0.1f, 0, 0.4f, 0, 0.2f, 0, 0, 0)
            .build();

    public static final AnimSet EMPIRE_BEE = new AnimSet(null, 1f, 1f, "head",
            BEE_IDLE,
            A.of(1.3f)
                    .rot("body", 0, 0, 0, 0, 0.4f, -20, 0, 0, 0.6f, 35, 0, 0, 1.0f, 30, 0, 0, 1.3f, 0, 0, 0)
                    .build(),
            A.of(1.5f)
                    .rot("body", 0, 0, 0, 0, 0.3f, 0, 0, 10, 0.5f, 0, 0, -10, 0.7f, 0, 0, 10, 0.9f, 0, 0, -10, 1.5f, 0, 0, 0)
                    .build(),
            A.of(1.7f)
                    .rot("body", 0, 0, 0, 0, 0.4f, -35, 0, 0, 1.4f, -35, 0, 0, 1.7f, 0, 0, 0)
                    .build());

    // ---- Quad Beetle -------------------------------------------------------------------
    public static final AnimSet QUAD_BEETLE = new AnimSet(
            A.of(0.6f).loop()
                    .rot("leg_l0", 0, 25, 0, 0, 0.3f, -25, 0, 0, 0.6f, 25, 0, 0)
                    .rot("leg_r1", 0, 25, 0, 0, 0.3f, -25, 0, 0, 0.6f, 25, 0, 0)
                    .rot("leg_l2", 0, 25, 0, 0, 0.3f, -25, 0, 0, 0.6f, 25, 0, 0)
                    .rot("leg_r0", 0, -25, 0, 0, 0.3f, 25, 0, 0, 0.6f, -25, 0, 0)
                    .rot("leg_l1", 0, -25, 0, 0, 0.3f, 25, 0, 0, 0.6f, -25, 0, 0)
                    .rot("leg_r2", 0, -25, 0, 0, 0.3f, 25, 0, 0, 0.6f, -25, 0, 0)
                    .build(),
            2f, 40f, "head",
            A.of(3f).loop()
                    .rot("head", 0, 0, 0, 0, 1.5f, 3, 0, 0, 3f, 0, 0, 0)
                    .build(),
            A.of(0.6f)
                    .rot("head", 0, 0, 0, 0, 0.15f, 15, 0, 0, 0.3f, -30, 0, 0, 0.6f, 0, 0, 0)
                    .build(),
            A.of(2.0f)
                    .rot("head", 0, 0, 0, 0, 0.3f, 20, 0, 0, 1.8f, 20, 0, 0, 2.0f, 0, 0, 0)
                    .pos("body", 0, 0, 0, 0, 0.3f, 0, -1, 0, 1.8f, 0, -1, 0, 2.0f, 0, 0, 0)
                    .build());

    // ---- Mud Digger --------------------------------------------------------------------
    public static final AnimSet MUD_DIGGER = new AnimSet(
            A.of(0.8f).loop()
                    .rot("claw_fl", 0, 30, 0, 0, 0.4f, -30, 0, 0, 0.8f, 30, 0, 0)
                    .rot("claw_fr", 0, -30, 0, 0, 0.4f, 30, 0, 0, 0.8f, -30, 0, 0)
                    .rot("leg_bl", 0, -25, 0, 0, 0.4f, 25, 0, 0, 0.8f, -25, 0, 0)
                    .rot("leg_br", 0, 25, 0, 0, 0.4f, -25, 0, 0, 0.8f, 25, 0, 0)
                    .rot("body", 0, 0, 0, -3, 0.4f, 0, 0, 3, 0.8f, 0, 0, -3)
                    .build(),
            1.6f, 40f, "head",
            A.of(3f).loop()
                    .rot("jaw", 0, 0, 0, 0, 1.5f, 6, 0, 0, 3f, 0, 0, 0)
                    .rot("tail", 0, 0, -8, 0, 1.5f, 0, 8, 0, 3f, 0, -8, 0)
                    .build(),
            A.of(0.8f)
                    .rot("jaw", 0, 0, 0, 0, 0.3f, 40, 0, 0, 0.45f, 0, 0, 0, 0.8f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.3f, -12, 0, 0, 0.45f, 8, 0, 0, 0.8f, 0, 0, 0)
                    .build(),
            A.of(3.2f)
                    .pos("root", 0, 0, 0, 0, 0.3f, 0, -24, 0, 2.45f, 0, -24, 0, 2.55f, 0, 4, 0, 2.8f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.3f, 30, 0, 0, 2.45f, 30, 0, 0, 2.55f, -30, 0, 0, 3.2f, 0, 0, 0)
                    .rot("jaw", 2.4f, 0, 0, 0, 2.55f, 45, 0, 0, 3.2f, 0, 0, 0)
                    .build(),
            A.of(1.1f)
                    .rot("root", 0, 0, 0, 0, 0.4f, 0, -40, 0, 0.6f, 0, 80, 0, 1.1f, 0, 0, 0)
                    .rot("tail", 0, 0, 0, 0, 0.4f, 0, 30, 0, 0.6f, 0, -40, 0, 1.1f, 0, 0, 0)
                    .build());

    // ---- Lycagon / Shadow wolves -------------------------------------------------------
    private static final AnimationDefinition WOLF_WALK = A.of(0.9f).loop()
            .rot("front_left_leg", 0, 25, 0, 0, 0.45f, -25, 0, 0, 0.9f, 25, 0, 0)
            .rot("back_right_leg", 0, 25, 0, 0, 0.45f, -25, 0, 0, 0.9f, 25, 0, 0)
            .rot("front_right_leg", 0, -25, 0, 0, 0.45f, 25, 0, 0, 0.9f, -25, 0, 0)
            .rot("back_left_leg", 0, -25, 0, 0, 0.45f, 25, 0, 0, 0.9f, -25, 0, 0)
            .pos("body", 0, 0, 0, 0, 0.225f, 0, 1, 0, 0.45f, 0, 0, 0, 0.675f, 0, 1, 0, 0.9f, 0, 0, 0)
            .rot("tail", 0, 0, -10, 0, 0.45f, 0, 10, 0, 0.9f, 0, -10, 0)
            .build();
    private static final AnimationDefinition WOLF_IDLE = A.of(3f).loop()
            .pos("body", 0, 0, 0, 0, 1.5f, 0, -0.6f, 0, 3f, 0, 0, 0)
            .rot("tail", 0, 0, -12, 0, 1.5f, 0, 12, 0, 3f, 0, -12, 0)
            .rot("left_ear", 0, 0, 0, 0, 2.2f, 0, 0, 0, 2.35f, 0, 0, -12, 2.5f, 0, 0, 0, 3f, 0, 0, 0)
            .build();

    public static final AnimSet LYCAGON = new AnimSet(WOLF_WALK, 1.4f, 30f, "head",
            WOLF_IDLE,
            // 1: bite
            A.of(0.7f)
                    .rot("head", 0, 0, 0, 0, 0.2f, -15, 0, 0, 0.4f, 20, 0, 0, 0.7f, 0, 0, 0)
                    .rot("jaw", 0, 0, 0, 0, 0.25f, 30, 0, 0, 0.42f, 0, 0, 0, 0.7f, 0, 0, 0)
                    .pos("body", 0, 0, 0, 0, 0.4f, 0, 0, -3, 0.7f, 0, 0, 0)
                    .build(),
            // 2: claw combo
            A.of(1.2f)
                    .rot("front_right_leg", 0, 0, 0, 0, 0.3f, -80, 0, -10, 0.45f, 30, 0, 0, 0.7f, -80, 0, 10, 0.85f, 30, 0, 0, 1.2f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.3f, -10, 0, 0, 0.45f, 5, 0, 0, 0.7f, -10, 0, 0, 0.85f, 5, 0, 0, 1.2f, 0, 0, 0)
                    .build(),
            // 3: shadow dash / pounce
            A.of(1.1f)
                    .pos("body", 0, 0, 0, 0, 0.45f, 0, -4, 0, 0.6f, 0, 2, -2, 1.1f, 0, 0, 0)
                    .rot("body", 0.45f, 0, 0, 0, 0.6f, -20, 0, 0, 1.1f, 0, 0, 0)
                    .rot("front_left_leg", 0.45f, 0, 0, 0, 0.6f, -50, 0, 0, 1.1f, 0, 0, 0)
                    .rot("front_right_leg", 0.45f, 0, 0, 0, 0.6f, -50, 0, 0, 1.1f, 0, 0, 0)
                    .build(),
            // 4: howl
            A.of(1.5f)
                    .rot("head", 0, 0, 0, 0, 0.4f, -45, 0, 0, 1.2f, -45, 0, 0, 1.5f, 0, 0, 0)
                    .rot("jaw", 0, 0, 0, 0, 0.4f, 35, 0, 0, 1.2f, 35, 0, 0, 1.5f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.4f, -10, 0, 0, 1.2f, -10, 0, 0, 1.5f, 0, 0, 0)
                    .build(),
            // 5: night rend (rear up and slam)
            A.of(1.8f)
                    .rot("body", 0, 0, 0, 0, 0.3f, -30, 0, 0, 1.2f, -30, 0, 0, 1.35f, 15, 0, 0, 1.8f, 0, 0, 0)
                    .rot("front_left_leg", 0, 0, 0, 0, 0.3f, -70, 0, 0, 1.2f, -70, 0, 0, 1.35f, 30, 0, 0, 1.8f, 0, 0, 0)
                    .rot("front_right_leg", 0, 0, 0, 0, 0.3f, -70, 0, 0, 1.2f, -70, 0, 0, 1.35f, 30, 0, 0, 1.8f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.3f, -25, 0, 0, 1.2f, -25, 0, 0, 1.35f, 20, 0, 0, 1.8f, 0, 0, 0)
                    .build());

    public static final AnimSet SHADOW_WOLF = new AnimSet(WOLF_WALK, 2.2f, 40f, "head",
            WOLF_IDLE,
            A.of(0.5f)
                    .rot("head", 0, 0, 0, 0, 0.15f, -15, 0, 0, 0.3f, 20, 0, 0, 0.5f, 0, 0, 0)
                    .rot("jaw", 0, 0, 0, 0, 0.15f, 30, 0, 0, 0.3f, 0, 0, 0)
                    .build());

    // ---- Wezaemon ----------------------------------------------------------------------
    public static final AnimSet WEZAEMON = new AnimSet(
            A.of(1.2f).loop()
                    .rot("left_leg", 0, 25, 0, 0, 0.6f, -25, 0, 0, 1.2f, 25, 0, 0)
                    .rot("right_leg", 0, -25, 0, 0, 0.6f, 25, 0, 0, 1.2f, -25, 0, 0)
                    .rot("left_arm", 0, -15, 0, 0, 0.6f, 15, 0, 0, 1.2f, -15, 0, 0)
                    .rot("right_arm", 0, 5, 0, 0, 0.6f, -5, 0, 0, 1.2f, 5, 0, 0)
                    .build(),
            1.5f, 40f, "head",
            A.of(4f).loop()
                    .rot("body", 0, 0, 0, 0, 2f, 2, 0, 0, 4f, 0, 0, 0)
                    .rot("right_arm", 0, -10, 0, 0, 2f, -14, 0, 0, 4f, -10, 0, 0)
                    .build(),
            // 1: iaijutsu dash
            A.of(1.4f)
                    .pos("root", 0, 0, 0, 0, 0.5f, 0, -2, 0, 0.6f, 0, -2, 0, 1.0f, 0, 0, 0)
                    .rot("right_arm", 0, 0, 0, 0, 0.5f, -30, 40, 0, 0.6f, -30, 40, 0, 0.7f, -85, -70, 0, 1.0f, -80, -70, 0, 1.4f, 0, 0, 0)
                    .rot("body", 0, 0, 0, 0, 0.5f, 10, 20, 0, 0.7f, 0, -30, 0, 1.0f, 0, -30, 0, 1.4f, 0, 0, 0)
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
                    .build(),
            // 4: stagger after being parried
            A.of(5f)
                    .rot("body", 0, 0, 0, 0, 0.3f, -15, 0, 0, 4.5f, -15, 0, 0, 5f, 0, 0, 0)
                    .rot("head", 0, 0, 0, 0, 0.3f, -20, 0, 0, 4.5f, -20, 0, 0, 5f, 0, 0, 0)
                    .rot("right_arm", 0, 0, 0, 0, 0.3f, 15, 0, 20, 4.5f, 15, 0, 20, 5f, 0, 0, 0)
                    .rot("left_arm", 0, 0, 0, 0, 0.3f, 15, 0, -20, 4.5f, 15, 0, -20, 5f, 0, 0, 0)
                    .pos("root", 0, 0, 0, 0, 0.3f, 0, -2, 0, 4.5f, 0, -2, 0, 5f, 0, 0, 0)
                    .build());
}
