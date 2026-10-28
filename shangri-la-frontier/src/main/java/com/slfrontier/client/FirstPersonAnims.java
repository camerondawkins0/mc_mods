package com.slfrontier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.slfrontier.SlfConfig;
import com.slfrontier.item.FrontierWeapon;
import com.slfrontier.skill.PlayerAnims;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;

/**
 * First-person versions of the skill animations: transforms applied to each hand in
 * camera space (x right, y up, -z forward) before vanilla positions the arm. Rotations
 * pivot around the camera, so they read as sweeping arcs across the view.
 */
public final class FirstPersonAnims {
    private FirstPersonAnims() {
    }

    /** Bell curve peaking at {@code peak}: 0 at t = 0 and t = 1. */
    private static float bump(float t, float peak) {
        if (t <= 0 || t >= 1) return 0;
        return t < peak ? Mth.sin(t / peak * Mth.HALF_PI) : Mth.cos((t - peak) / (1 - peak) * Mth.HALF_PI);
    }

    public static void apply(AbstractClientPlayer player, InteractionHand hand, PoseStack pose, float partialTick, float swing) {
        if (!SlfConfig.FIRST_PERSON_SKILL_ANIMS.get()) return;
        boolean main = hand == InteractionHand.MAIN_HAND;
        float side = main ? 1 : -1;  // mirror for the off hand (right-handed players)
        int anim = ClientAnims.playing(player.getId());
        float t = ClientAnims.progress(player.getId(), partialTick);
        if (anim >= 0 && t >= 0) {
            skill(anim, t, side, main, pose);
        } else if (swing > 0 && main && player.getMainHandItem().getItem() instanceof FrontierWeapon) {
            // A wider diagonal arc on top of vanilla's swing for Frontier weapons.
            float b = Mth.sin(swing * Mth.PI);
            pose.mulPose(Axis.ZP.rotationDegrees(-28 * b));
            pose.translate(-0.12f * b, 0.05f * b, -0.08f * b);
        }
    }

    private static void skill(int anim, float t, float side, boolean main, PoseStack pose) {
        switch (anim) {
            case PlayerAnims.DASH -> {
                float b = bump(t, 0.25f);
                pose.translate(side * 0.12f * b, -0.18f * b, 0.15f * b);
                pose.mulPose(Axis.XP.rotationDegrees(25 * b));
            }
            case PlayerAnims.BACKSTEP -> {
                float b = bump(t, 0.3f);
                pose.translate(0, 0.08f * b, -0.1f * b);
                pose.mulPose(Axis.XP.rotationDegrees(-15 * b));
            }
            case PlayerAnims.PIERCE, PlayerAnims.RABBIT_GUN -> {
                if (main) {
                    float coil = bump(t, 0.25f) * (t < 0.4f ? 1 : 0);
                    float thrust = t < 0.35f ? 0 : bump((t - 0.35f) / 0.65f, 0.25f);
                    pose.translate(0.08f * coil - 0.18f * thrust, 0.02f * thrust, 0.25f * coil - 0.65f * thrust);
                    pose.mulPose(Axis.YP.rotationDegrees(-10 * thrust));
                    pose.mulPose(Axis.XP.rotationDegrees(-25 * thrust));
                } else {
                    pose.translate(0, -0.2f * bump(t, 0.4f), 0.1f * bump(t, 0.4f));
                }
            }
            case PlayerAnims.PARRY -> {
                float b = Math.min(1, t * 6) * (t > 0.85f ? (1 - t) / 0.15f : 1);
                pose.translate(-side * 0.32f * b, 0.12f * b, 0.05f * b);
                pose.mulPose(Axis.ZP.rotationDegrees(side * 40 * b));
            }
            case PlayerAnims.PARRY_HIT -> {
                float b = bump(t, 0.3f);
                pose.translate(side * 0.25f * b, 0.15f * b, 0.1f * b);
                pose.mulPose(Axis.ZP.rotationDegrees(-side * 30 * b));
            }
            case PlayerAnims.SPIN, PlayerAnims.COUNTER -> {
                float b = bump(t, 0.5f);
                pose.mulPose(Axis.YP.rotationDegrees(side * 70 * Mth.sin(t * Mth.TWO_PI)));
                pose.translate(side * 0.15f * b, -0.05f * b, 0);
            }
            case PlayerAnims.CRESCENT -> {
                // Both blades raised wide, then crossing down into an X.
                float up = t < 0.3f ? bump(t / 0.3f * 0.5f, 0.5f) : 0;
                float down = t < 0.3f ? 0 : bump((t - 0.3f) / 0.7f, 0.35f);
                pose.mulPose(Axis.ZP.rotationDegrees(side * (-35 * up + 45 * down)));
                pose.translate(side * (0.15f * up - 0.25f * down), 0.25f * up - 0.15f * down, -0.1f * down);
            }
            case PlayerAnims.LEAP -> {
                float b = bump(t, 0.3f);
                pose.translate(0, 0.25f * b, 0.1f * b);
                pose.mulPose(Axis.XP.rotationDegrees(-30 * b));
            }
            case PlayerAnims.SLAM -> {
                float raise = t < 0.35f ? 1 - t / 0.35f : 0;
                float hit = t < 0.35f ? t / 0.35f : bump(0.5f + (t - 0.35f) / 1.3f, 0.5f);
                pose.translate(-side * 0.1f, 0.4f * raise - 0.25f * hit, -0.2f * hit);
                pose.mulPose(Axis.XP.rotationDegrees(-60 * raise + 35 * hit));
            }
            case PlayerAnims.SKY_CLEAR -> {
                float raise = t < 0.5f ? bump(t / 0.5f * 0.5f, 0.5f) : Math.max(0, 1 - (t - 0.5f) * 8);
                float cut = t < 0.55f ? 0 : bump((t - 0.55f) / 0.45f, 0.4f);
                pose.translate(0, 0.45f * raise, 0.1f * raise);
                pose.mulPose(Axis.XP.rotationDegrees(-50 * raise));
                pose.mulPose(Axis.YP.rotationDegrees(side * 120 * cut));
            }
            case PlayerAnims.DRAW -> {
                if (main) {
                    pose.translate(-0.25f, -0.15f, 0.1f);
                    pose.mulPose(Axis.YP.rotationDegrees(35));
                    pose.mulPose(Axis.ZP.rotationDegrees(-20));
                }
            }
            case PlayerAnims.IAI -> {
                if (main) {
                    float cut = bump(t, 0.3f);
                    float from = Math.max(0, 1 - t * 3);
                    pose.translate(-0.25f * from + 0.25f * cut, -0.15f * from, 0.1f * from - 0.2f * cut);
                    pose.mulPose(Axis.YP.rotationDegrees(35 * from - 60 * cut));
                }
            }
            case PlayerAnims.OFFHAND -> {
                if (!main) {
                    float b = bump(t, 0.4f);
                    pose.mulPose(Axis.ZP.rotationDegrees(30 * b));
                    pose.translate(0.2f * b, 0.08f * b, -0.15f * b);
                }
            }
            default -> {
            }
        }
    }
}
