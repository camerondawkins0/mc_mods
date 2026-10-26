package com.slfrontier.client;

import com.slfrontier.skill.PlayerAnims;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Third-person skill poses for players. The server tells every tracking client which
 * pose to play; the HumanoidModel mixin bends the limbs, and whole-body spins are
 * applied to the pose stack in {@link ClientEvents}.
 */
public final class ClientAnims {
    private record Active(int anim, float start, int duration) {
    }

    private static final Map<Integer, Active> ACTIVE = new ConcurrentHashMap<>();
    private static float clock;

    private ClientAnims() {
    }

    public static void tick() {
        clock++;
        ACTIVE.values().removeIf(a -> clock - a.start > a.duration + 2);
    }

    public static void start(int entityId, int anim, int duration) {
        ACTIVE.put(entityId, new Active(anim, clock, Math.max(1, duration)));
    }

    /** 0..1 progress of the active pose, or -1 if none. */
    private static float progress(Active a, float partialTick) {
        return Mth.clamp((clock + partialTick - a.start) / a.duration, 0f, 1f);
    }

    /** Degrees of whole-body yaw to add while rendering (spins), or 0. */
    public static float bodySpin(int entityId, float partialTick) {
        Active a = ACTIVE.get(entityId);
        if (a == null) return 0f;
        float p = progress(a, partialTick);
        return switch (a.anim) {
            case PlayerAnims.SPIN -> p * 360f;
            case PlayerAnims.SKY_CLEAR -> p * 720f;
            default -> 0f;
        };
    }

    public static void apply(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        Active a = ACTIVE.get(entity.getId());
        if (a == null) return;
        float partial = ageInTicks - (float) Math.floor(ageInTicks);
        float p = progress(a, partial);
        float swing = Mth.sin(p * Mth.PI);
        switch (a.anim) {
            case PlayerAnims.DASH -> {
                model.body.xRot = 0.5f * swing;
                model.rightArm.xRot = 1.0f * swing;
                model.leftArm.xRot = 1.0f * swing;
                model.rightLeg.xRot = -0.6f * swing;
                model.leftLeg.xRot = 0.6f * swing;
                model.head.xRot -= 0.3f * swing;
            }
            case PlayerAnims.PIERCE -> {
                model.rightArm.xRot = -1.6f;
                model.rightArm.yRot = -0.1f;
                model.leftArm.xRot = 0.8f * swing;
                model.body.yRot = -0.35f * swing;
                model.rightLeg.xRot = -0.5f * swing;
                model.leftLeg.xRot = 0.5f * swing;
            }
            case PlayerAnims.PARRY -> {
                model.rightArm.xRot = -1.35f;
                model.rightArm.yRot = -0.7f;
                model.leftArm.xRot = -1.35f;
                model.leftArm.yRot = 0.7f;
                model.body.xRot = 0.15f;
            }
            case PlayerAnims.SPIN, PlayerAnims.SKY_CLEAR -> {
                model.rightArm.zRot = 1.45f;
                model.leftArm.zRot = -1.45f;
                model.rightArm.xRot = -0.2f;
                model.leftArm.xRot = -0.2f;
            }
            case PlayerAnims.CRESCENT -> {
                float arm = Mth.lerp(p, -2.9f, 0.4f);
                model.rightArm.xRot = arm;
                model.leftArm.xRot = arm;
                model.rightArm.yRot = -0.25f;
                model.leftArm.yRot = 0.25f;
                model.body.xRot = 0.4f * p;
            }
            case PlayerAnims.OFFHAND -> {
                model.leftArm.xRot = -1.8f * swing;
                model.leftArm.yRot = 0.4f * swing;
                model.body.yRot = 0.3f * swing;
            }
            case PlayerAnims.LEAP -> {
                model.rightArm.xRot = -2.7f;
                model.leftArm.xRot = -2.7f;
                model.rightLeg.xRot = -0.7f;
                model.leftLeg.xRot = 0.3f;
            }
            case PlayerAnims.IAI -> {
                model.rightArm.xRot = -1.4f;
                model.rightArm.yRot = Mth.lerp(p, 0.9f, -1.3f);
                model.body.yRot = Mth.lerp(p, 0.4f, -0.5f);
                model.rightLeg.xRot = -0.6f;
                model.leftLeg.xRot = 0.5f;
            }
            default -> {
            }
        }
    }
}
