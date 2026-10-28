package com.slfrontier.client.anim;

import com.slfrontier.client.anim.PlayerAnim.Ease;
import com.slfrontier.client.anim.PlayerAnim.Part;
import com.slfrontier.skill.PlayerAnims;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Player skill and attack animations. Limb rotations are in degrees in vanilla model
 * space (arm x -90 points forward, -180 straight up; right arm +z swings outward, left
 * arm -z). ROOT spins/leans the whole body.
 */
public final class SkillAnims {
    private static final Map<Integer, PlayerAnim> BY_ID = new HashMap<>();

    private SkillAnims() {
    }

    @Nullable
    public static PlayerAnim get(int id) {
        return BY_ID.get(id);
    }

    private static void put(int id, PlayerAnim anim) {
        BY_ID.put(id, anim);
    }

    static {
        // Tap Step / Slide Step: a low, skating glide with blades trailing behind.
        put(PlayerAnims.DASH, PlayerAnim.of(8)
                .ease(Ease.OUT)
                .spin(0, 0, 0, 0, 0.25f, 0, 28, 0, 0.8f, 0, 24, 0, 1, 0, 0, 0)
                .move(0, 0, 0, 0.25f, -0.18f, 0, 0.8f, -0.12f, 0, 1, 0, 0)
                .rot(Part.RIGHT_ARM, 0, 0, 0, 10, 0.25f, 45, 10, 30, 1, 30, 0, 20)
                .rot(Part.LEFT_ARM, 0, 0, 0, -10, 0.25f, 45, -10, -30, 1, 30, 0, -20)
                .rot(Part.RIGHT_LEG, 0, 0, 0, 0, 0.25f, -40, 0, 8, 1, -25, 0, 5)
                .rot(Part.LEFT_LEG, 0, 0, 0, 0, 0.25f, 50, 0, -8, 1, 35, 0, -5)
                .rot(Part.HEAD, 0, 0, 0, 0, 0.25f, -25, 0, 0, 1, -20, 0, 0)
                .build());
        put(PlayerAnims.BACKSTEP, PlayerAnim.of(8)
                .ease(Ease.OUT)
                .spin(0, 0, 0, 0, 0.3f, 0, -16, 0, 1, 0, 0, 0)
                .move(0, 0, 0, 0.3f, -0.08f, -0.15f, 1, 0, 0)
                .rot(Part.RIGHT_ARM, 0, 0, 0, 0, 0.3f, -70, -25, 0, 1, -50, -10, 0)
                .rot(Part.LEFT_ARM, 0, 0, 0, 0, 0.3f, -70, 25, 0, 1, -50, 10, 0)
                .rot(Part.RIGHT_LEG, 0, 0, 0, 0, 0.3f, 30, 0, 0, 1, 15, 0, 0)
                .rot(Part.LEFT_LEG, 0, 0, 0, 0, 0.3f, -25, 0, 0, 1, -10, 0, 0)
                .build());

        // Drill Piercer / Glowing Pierce: coil, then a full-extension lunging thrust.
        put(PlayerAnims.PIERCE, PlayerAnim.of(11)
                .ease(Ease.OUT)
                .spin(0, 0, 0, 0, 0.3f, 30, 4, 0)
                .ease(Ease.BACK)
                .spin(0.45f, -12, 16, 0, 0.8f, -10, 14, 0)
                .ease(Ease.IN_OUT)
                .spin(1, 0, 0, 0)
                .ease(Ease.OUT)
                .move(0, 0, 0, 0.3f, -0.12f, -0.05f, 0.45f, -0.15f, 0.3f, 0.8f, -0.12f, 0.25f, 1, 0, 0)
                .rot(Part.RIGHT_ARM, 0, -30, 20, 0, 0.3f, -75, 65, 10)
                .ease(Ease.BACK)
                .rot(Part.RIGHT_ARM, 0.45f, -95, -5, 0, 0.8f, -92, 0, 0)
                .rot(Part.LEFT_ARM, 0, 0, 0, 0, 0.3f, -40, -20, -10, 0.45f, 55, 0, -15, 1, 0, 0, 0)
                .rot(Part.RIGHT_LEG, 0, 0, 0, 0, 0.3f, 10, 0, 0, 0.45f, -55, 0, 5, 0.8f, -50, 0, 5, 1, 0, 0, 0)
                .rot(Part.LEFT_LEG, 0, 0, 0, 0, 0.3f, -10, 0, 0, 0.45f, 40, 0, -5, 0.8f, 35, 0, -5, 1, 0, 0, 0)
                .build());

        // Perfect Parry: snap both blades into a crossed X guard ("Parrying Protect").
        put(PlayerAnims.PARRY, PlayerAnim.of(12).blend(1.5f, 3f)
                .ease(Ease.BACK)
                .spin(0, 0, 0, 0, 0.15f, 0, -6, 0, 1, 0, -4, 0)
                .rot(Part.RIGHT_ARM, 0, -20, 0, 0, 0.15f, -112, -42, 8, 1, -108, -40, 6)
                .rot(Part.LEFT_ARM, 0, -20, 0, 0, 0.15f, -112, 42, -8, 1, -108, 40, -6)
                .rot(Part.RIGHT_LEG, 0, 0, 0, 0, 0.15f, -15, 0, 6, 1, -15, 0, 6)
                .rot(Part.LEFT_LEG, 0, 0, 0, 0, 0.15f, 15, 0, -6, 1, 15, 0, -6)
                .build());
        put(PlayerAnims.PARRY_HIT, PlayerAnim.of(7)
                .ease(Ease.OUT)
                .spin(0, 0, -6, 0, 0.3f, 0, -14, 0, 1, 0, 0, 0)
                .move(0, 0, 0, 0.3f, 0, -0.12f, 1, 0, 0)
                .rot(Part.RIGHT_ARM, 0, -110, -40, 6, 0.3f, -70, 35, 45, 1, -40, 10, 15)
                .rot(Part.LEFT_ARM, 0, -110, 40, -6, 0.3f, -70, -35, -45, 1, -40, -10, -15)
                .build());
        // Repel Counter: deflect into a whirling backhand.
        put(PlayerAnims.COUNTER, PlayerAnim.of(9)
                .ease(Ease.OUT)
                .spin(0, 0, 0, 0, 0.6f, -150, 8, 0, 1, -360, 0, 0)
                .rot(Part.RIGHT_ARM, 0, -100, 60, 20, 0.6f, -90, -70, 60, 1, -20, 0, 10)
                .rot(Part.LEFT_ARM, 0, -60, -20, -40, 0.6f, -50, 30, -70, 1, -10, 0, -10)
                .build());

        // Cyclone Slash: blades thrown wide, one fast full turn from a crouch.
        put(PlayerAnims.SPIN, PlayerAnim.of(11)
                .ease(Ease.IN_OUT)
                .spin(0, 0, 0, 0, 0.15f, 25, 6, 0)
                .ease(Ease.OUT)
                .spin(0.85f, -360, 8, 0, 1, -360, 0, 0)
                .move(0, 0, 0, 0.15f, -0.15f, 0, 0.85f, -0.12f, 0, 1, 0, 0)
                .rot(Part.RIGHT_ARM, 0, -20, 0, 20, 0.15f, -10, 0, 95, 0.85f, -15, 0, 90, 1, -10, 0, 30)
                .rot(Part.LEFT_ARM, 0, -20, 0, -20, 0.15f, -10, 0, -95, 0.85f, -15, 0, -90, 1, -10, 0, -30)
                .rot(Part.RIGHT_LEG, 0, 0, 0, 0, 0.15f, -10, 0, 15, 1, 0, 0, 0)
                .rot(Part.LEFT_LEG, 0, 0, 0, 0, 0.15f, 10, 0, -15, 1, 0, 0, 0)
                .build());

        // Moon Reflection: both moonblades sweep from high and wide into a low crossed crescent.
        put(PlayerAnims.CRESCENT, PlayerAnim.of(12)
                .ease(Ease.OUT)
                .spin(0, 0, 0, 0, 0.3f, 0, -8, 0)
                .ease(Ease.BACK)
                .spin(0.55f, 0, 18, 0, 1, 0, 0, 0)
                .move(0, 0, 0, 0.3f, 0.05f, 0, 0.55f, -0.18f, 0.2f, 1, 0, 0)
                .ease(Ease.OUT)
                .rot(Part.RIGHT_ARM, 0, -40, 0, 20, 0.3f, -165, 10, 45)
                .ease(Ease.BACK)
                .rot(Part.RIGHT_ARM, 0.55f, -35, -55, -25, 0.85f, -40, -50, -20)
                .rot(Part.RIGHT_ARM, 1, -20, 0, 0)
                .ease(Ease.OUT)
                .rot(Part.LEFT_ARM, 0, -40, 0, -20, 0.3f, -165, -10, -45)
                .ease(Ease.BACK)
                .rot(Part.LEFT_ARM, 0.55f, -35, 55, 25, 0.85f, -40, 50, 20)
                .rot(Part.LEFT_ARM, 1, -20, 0, 0)
                .rot(Part.RIGHT_LEG, 0, 0, 0, 0, 0.55f, -35, 0, 5, 1, 0, 0, 0)
                .rot(Part.LEFT_LEG, 0, 0, 0, 0, 0.55f, 30, 0, -5, 1, 0, 0, 0)
                .build());

        // Jumps: arms swing up, knees tuck.
        put(PlayerAnims.LEAP, PlayerAnim.of(12)
                .ease(Ease.OUT)
                .spin(0, 0, 0, 0, 0.3f, 0, -12, 0, 1, 0, 0, 0)
                .rot(Part.RIGHT_ARM, 0, 20, 0, 10, 0.3f, -165, 0, 20, 1, -150, 0, 15)
                .rot(Part.LEFT_ARM, 0, 20, 0, -10, 0.3f, -165, 0, -20, 1, -150, 0, -15)
                .rot(Part.RIGHT_LEG, 0, 0, 0, 0, 0.3f, -70, 0, 0, 1, -45, 0, 0)
                .rot(Part.LEFT_LEG, 0, 0, 0, 0, 0.3f, -20, 0, 0, 1, 10, 0, 0)
                .build());
        // Crescent Vorpal: the Twin Moon raised overhead, then driven into the ground.
        put(PlayerAnims.SLAM, PlayerAnim.of(12)
                .ease(Ease.IN)
                .spin(0, 0, -10, 0, 0.35f, 0, 38, 0)
                .ease(Ease.OUT)
                .spin(1, 0, 0, 0)
                .ease(Ease.IN)
                .move(0, 0, 0, 0.35f, -0.3f, 0.25f)
                .ease(Ease.OUT)
                .move(1, 0, 0)
                .ease(Ease.IN)
                .rot(Part.RIGHT_ARM, 0, -175, -10, 0, 0.35f, -30, -15, 0)
                .ease(Ease.OUT)
                .rot(Part.RIGHT_ARM, 1, -20, 0, 0)
                .ease(Ease.IN)
                .rot(Part.LEFT_ARM, 0, -175, 10, 0, 0.35f, -30, 15, 0)
                .ease(Ease.OUT)
                .rot(Part.LEFT_ARM, 1, -20, 0, 0)
                .rot(Part.RIGHT_LEG, 0, 0, 0, 0, 0.35f, -50, 0, 10, 1, 0, 0, 0)
                .rot(Part.LEFT_LEG, 0, 0, 0, 0, 0.35f, 40, 0, -10, 1, 0, 0, 0)
                .build());

        // Sky Clear: blade raised to the heavens, a beat of stillness, then a full circular cut.
        put(PlayerAnims.SKY_CLEAR, PlayerAnim.of(16)
                .ease(Ease.OUT)
                .spin(0, 0, 0, 0, 0.35f, 0, -10, 0, 0.5f, 0, -12, 0)
                .ease(Ease.IN_OUT)
                .spin(0.8f, -360, 10, 0, 1, -360, 0, 0)
                .move(0, 0, 0, 0.35f, 0.05f, 0, 0.5f, 0.05f, 0, 0.6f, -0.15f, 0, 1, 0, 0)
                .ease(Ease.OUT)
                .rot(Part.RIGHT_ARM, 0, -30, 0, 0, 0.35f, -178, 0, 5, 0.5f, -178, 0, 5)
                .ease(Ease.IN_OUT)
                .rot(Part.RIGHT_ARM, 0.6f, -90, 0, 80, 0.8f, -90, 0, 85, 1, -30, 0, 10)
                .rot(Part.LEFT_ARM, 0, 0, 0, 0, 0.35f, -60, 30, -20, 0.6f, -20, 0, -60, 1, 0, 0, 0)
                .rot(Part.HEAD, 0, 0, 0, 0, 0.35f, -35, 0, 0, 0.5f, -35, 0, 0, 0.6f, 0, 0, 0)
                .build());

        // Katana: crouched draw stance (held while charging), then the iaijutsu cut.
        put(PlayerAnims.DRAW, PlayerAnim.of(40).blend(3f, 1f)
                .spin(0, 30, 6, 0, 1, 30, 6, 0)
                .move(0, -0.2f, 0, 1, -0.2f, 0)
                .rot(Part.RIGHT_ARM, 0, -45, 75, 0, 1, -45, 75, 0)
                .rot(Part.LEFT_ARM, 0, -40, 50, 0, 1, -40, 50, 0)
                .rot(Part.RIGHT_LEG, 0, -45, 0, 5, 1, -45, 0, 5)
                .rot(Part.LEFT_LEG, 0, 40, 0, -5, 1, 40, 0, -5)
                .build());
        put(PlayerAnims.IAI, PlayerAnim.of(11).blend(0.5f, 3f)
                .ease(Ease.OUT)
                .spin(0, 30, 6, 0, 0.35f, -45, 12, 0, 1, 0, 0, 0)
                .move(0, -0.2f, 0, 0.35f, -0.12f, 0.35f, 1, 0, 0)
                .rot(Part.RIGHT_ARM, 0, -45, 75, 0, 0.35f, -95, -85, 0, 0.8f, -90, -80, 0, 1, -20, 0, 0)
                .rot(Part.LEFT_ARM, 0, -40, 50, 0, 0.35f, 20, 0, -20, 1, 0, 0, 0)
                .rot(Part.RIGHT_LEG, 0, -45, 0, 5, 0.35f, -55, 0, 5, 1, 0, 0, 0)
                .rot(Part.LEFT_LEG, 0, 40, 0, -5, 0.35f, 45, 0, -5, 1, 0, 0, 0)
                .build());

        // Rabbit Gun: Emul clings to the right wrist and the player is launched at the target.
        put(PlayerAnims.RABBIT_GUN, PlayerAnim.of(12)
                .ease(Ease.OUT)
                .spin(0, 0, 0, 0, 0.25f, 0, 35, 0, 0.8f, 0, 30, 0, 1, 0, 0, 0)
                .rot(Part.RIGHT_ARM, 0, -60, 0, 0, 0.25f, -92, -5, 0, 0.8f, -90, 0, 0, 1, -30, 0, 0)
                .rot(Part.LEFT_ARM, 0, 0, 0, 0, 0.25f, 50, 0, -25, 1, 0, 0, 0)
                .rot(Part.RIGHT_LEG, 0, 0, 0, 0, 0.25f, 30, 0, 0, 1, 0, 0, 0)
                .rot(Part.LEFT_LEG, 0, 0, 0, 0, 0.25f, 50, 0, 0, 1, 0, 0, 0)
                .build());

        // Off-hand follow-up strike of twin blades.
        put(PlayerAnims.OFFHAND, PlayerAnim.of(7)
                .ease(Ease.OUT)
                .spin(0, 0, 0, 0, 0.4f, 18, 4, 0, 1, 0, 0, 0)
                .rot(Part.LEFT_ARM, 0, -120, 20, -30, 0.4f, -40, -55, 10, 1, -20, 0, 0)
                .build());

        // Basic twin-blade swings (driven by the vanilla swing timer, not by packets).
        put(PlayerAnims.SLASH_A, PlayerAnim.of(6).blend(0.5f, 2f)
                .ease(Ease.OUT)
                .spin(0, -12, 2, 0, 0.5f, 14, 6, 0, 1, 0, 0, 0)
                .rot(Part.RIGHT_ARM, 0, -150, 25, 25, 0.5f, -35, -40, -15, 1, -25, -10, 0)
                .build());
        put(PlayerAnims.SLASH_B, PlayerAnim.of(6).blend(0.5f, 2f)
                .ease(Ease.OUT)
                .spin(0, 16, 2, 0, 0.5f, -14, 6, 0, 1, 0, 0, 0)
                .rot(Part.RIGHT_ARM, 0, -95, -65, -10, 0.5f, -75, 55, 40, 1, -30, 10, 10)
                .build());
        put(PlayerAnims.SLASH_HEAVY, PlayerAnim.of(8).blend(0.5f, 2f)
                .ease(Ease.OUT)
                .spin(0, 0, -8, 0, 0.5f, 0, 18, 0, 1, 0, 0, 0)
                .rot(Part.RIGHT_ARM, 0, -170, -5, 5, 0.5f, -30, -10, 0, 1, -25, 0, 0)
                .rot(Part.LEFT_ARM, 0, -170, 5, -5, 0.5f, -30, 10, 0, 1, -25, 0, 0)
                .build());
    }
}
