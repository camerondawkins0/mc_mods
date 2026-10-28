package com.slfrontier.client;

import com.slfrontier.client.anim.PlayerAnim;
import com.slfrontier.client.anim.SkillAnims;
import com.slfrontier.item.FrontierWeapon;
import com.slfrontier.skill.PlayerAnims;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays keyframed {@link PlayerAnim}s on players. Skill animations are started by the
 * server (PlayAnimation packets); swings with Frontier weapons are replaced by slashes
 * driven by the vanilla swing timer. The HumanoidModel mixin bends limbs through
 * {@link #apply}; whole-body motion is applied to the pose stack in {@link ClientEvents}.
 */
public final class ClientAnims {
    private record Active(int anim, float start, int duration) {
    }

    /** What is playing on an entity this frame, and how strongly. */
    public record Sample(PlayerAnim anim, float t, float weight) {
    }

    private static final Map<Integer, Active> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> LAST_SWING = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> SWING_VARIANT = new ConcurrentHashMap<>();
    private static float clock;

    private ClientAnims() {
    }

    public static void tick() {
        clock++;
        ACTIVE.values().removeIf(a -> clock - a.start > a.duration + 2);
    }

    public static void start(int entityId, int anim, int duration) {
        PlayerAnim def = SkillAnims.get(anim);
        int length = duration > 0 ? duration : def != null ? def.ticks : 10;
        ACTIVE.put(entityId, new Active(anim, clock, Math.max(1, length)));
    }

    public static boolean isPlaying(int entityId) {
        return ACTIVE.containsKey(entityId);
    }

    public static int playing(int entityId) {
        Active a = ACTIVE.get(entityId);
        return a == null ? -1 : a.anim;
    }

    /** Elapsed fraction of the server-started animation on an entity, or -1. */
    public static float progress(int entityId, float partialTick) {
        Active a = ACTIVE.get(entityId);
        return a == null ? -1 : Mth.clamp((clock + partialTick - a.start) / a.duration, 0f, 1f);
    }

    /** The animation affecting an entity right now: a skill, else a weapon slash, else null. */
    @Nullable
    public static Sample sample(LivingEntity entity, float partialTick) {
        Active a = ACTIVE.get(entity.getId());
        if (a != null) {
            PlayerAnim def = SkillAnims.get(a.anim);
            if (def != null) {
                float elapsed = clock + partialTick - a.start;
                float t = Mth.clamp(elapsed / a.duration, 0f, 1f);
                float in = def.blendIn <= 0 ? 1 : Mth.clamp(elapsed / def.blendIn, 0f, 1f);
                float out = def.blendOut <= 0 ? 1 : Mth.clamp((a.duration - elapsed) / def.blendOut, 0f, 1f);
                return new Sample(def, t, Math.min(in, out));
            }
        }
        float attack = entity.getAttackAnim(partialTick);
        if (attack <= 0 || !(entity.getMainHandItem().getItem() instanceof FrontierWeapon weapon)) {
            LAST_SWING.remove(entity.getId());
            return null;
        }
        Float last = LAST_SWING.put(entity.getId(), attack);
        if (last == null || attack < last - 0.05f) SWING_VARIANT.merge(entity.getId(), 1, Integer::sum);
        int id;
        if (entity.swingingArm == InteractionHand.OFF_HAND) {
            id = PlayerAnims.OFFHAND;
        } else if (weapon.kind() == FrontierWeapon.Kind.GREATSWORD) {
            id = PlayerAnims.SLASH_HEAVY;
        } else {
            id = SWING_VARIANT.getOrDefault(entity.getId(), 0) % 2 == 0 ? PlayerAnims.SLASH_A : PlayerAnims.SLASH_B;
        }
        PlayerAnim def = SkillAnims.get(id);
        if (def == null) return null;
        float elapsed = attack * def.ticks;
        return new Sample(def, attack, def.weight(elapsed));
    }

    /** Whole-body transform for the renderer, already scaled by the blend weight. */
    public static PlayerAnim.Root root(LivingEntity entity, float partialTick) {
        Sample s = sample(entity, partialTick);
        if (s == null || s.weight() <= 0) return PlayerAnim.Root.NONE;
        PlayerAnim.Root r = s.anim().root(s.t());
        float w = s.weight();
        return new PlayerAnim.Root(r.yaw() * w, r.pitch() * w, r.roll() * w, r.dy() * w, r.dz() * w);
    }

    public static void apply(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        float partial = ageInTicks - (float) Math.floor(ageInTicks);
        Sample s = sample(entity, partial);
        if (s == null || s.weight() <= 0) return;
        float w = s.weight();
        set(model.head, s, PlayerAnim.Part.HEAD, w);
        set(model.body, s, PlayerAnim.Part.BODY, w);
        set(model.rightArm, s, PlayerAnim.Part.RIGHT_ARM, w);
        set(model.leftArm, s, PlayerAnim.Part.LEFT_ARM, w);
        set(model.rightLeg, s, PlayerAnim.Part.RIGHT_LEG, w);
        set(model.leftLeg, s, PlayerAnim.Part.LEFT_LEG, w);
        if (s.anim().has(PlayerAnim.Part.BODY)) {
            // Keep the shoulders attached when the torso twists (as vanilla's attack animation does).
            model.rightArm.z = Mth.sin(model.body.yRot) * 5f;
            model.rightArm.x = -Mth.cos(model.body.yRot) * 5f;
            model.leftArm.z = -Mth.sin(model.body.yRot) * 5f;
            model.leftArm.x = Mth.cos(model.body.yRot) * 5f;
        }
    }

    private static void set(ModelPart part, Sample s, PlayerAnim.Part channel, float w) {
        float[] v = s.anim().sample(channel, s.t());
        if (v == null) return;
        part.xRot = Mth.lerp(w, part.xRot, v[0] * Mth.DEG_TO_RAD);
        part.yRot = Mth.lerp(w, part.yRot, v[1] * Mth.DEG_TO_RAD);
        part.zRot = Mth.lerp(w, part.zRot, v[2] * Mth.DEG_TO_RAD);
    }
}
