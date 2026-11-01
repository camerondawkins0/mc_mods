package com.slfrontier.client;

import com.slfrontier.SLFrontier;
import com.slfrontier.client.anim.PlayerAnim;
import com.slfrontier.client.anim.SkillAnims;
import com.slfrontier.client.fx.Impact;
import com.slfrontier.item.FrontierWeapon;
import com.slfrontier.skill.PlayerAnims;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays keyframed {@link PlayerAnim}s on players. Skill animations are started by the
 * server (PlayAnimation packets); swings with Frontier weapons are replaced by slashes
 * driven by the vanilla swing timer. Each frame {@link #extract} puts what is playing into the
 * player's render state; the HumanoidModel mixin bends limbs from it through {@link #apply} and
 * whole-body motion is applied to the pose stack in {@link ClientEvents}.
 */
public final class ClientAnims {
    private record Active(int anim, float start, int duration) {
    }

    /** What is playing on an entity this frame, and how strongly. */
    public record Sample(PlayerAnim anim, float t, float weight) {
    }

    /** The animation sample and whole-body transform carried on a player's render state. */
    public static final ContextKey<Sample> SAMPLE = new ContextKey<>(SLFrontier.id("player_anim"));
    public static final ContextKey<PlayerAnim.Root> ROOT = new ContextKey<>(SLFrontier.id("player_anim_root"));

    private static final Map<Integer, Active> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> LAST_SWING = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> SWING_VARIANT = new ConcurrentHashMap<>();
    private static float clock;

    private ClientAnims() {
    }

    public static void tick() {
        clock++;
        ACTIVE.entrySet().removeIf(e -> elapsed(e.getKey(), e.getValue(), 0) > e.getValue().duration + 2);
    }

    /** The animation clock in ticks (hit-stop windows are measured on it). */
    public static float clock() {
        return clock;
    }

    /** Ticks an animation has played, not counting time it spent held by a hit-stop. */
    private static float elapsed(int entityId, Active a, float partialTick) {
        float now = clock + partialTick;
        return now - a.start - Impact.lost(entityId, a.start, now);
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
        return a == null ? -1 : Mth.clamp(elapsed(entityId, a, partialTick) / a.duration, 0f, 1f);
    }

    /** The animation affecting an entity right now: a skill, else a weapon slash, else null. */
    @Nullable
    public static Sample sample(LivingEntity entity, float partialTick) {
        Active a = ACTIVE.get(entity.getId());
        if (a != null) {
            PlayerAnim def = SkillAnims.get(a.anim);
            if (def != null) {
                float elapsed = elapsed(entity.getId(), a, partialTick);
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

    /** Render state modifier for players: samples what is playing on them for this frame's model and pose. */
    public static void extract(LivingEntity entity, EntityRenderState state) {
        Sample s = sample(entity, state.partialTick);
        if (s == null || s.weight() <= 0) return;
        state.setRenderData(SAMPLE, s);
        PlayerAnim.Root root = root(s);
        if (root != PlayerAnim.Root.NONE) state.setRenderData(ROOT, root);
    }

    /** Whole-body transform for the renderer, already scaled by the blend weight. */
    private static PlayerAnim.Root root(Sample s) {
        PlayerAnim.Root r = s.anim().root(s.t());
        if (r.equals(PlayerAnim.Root.NONE)) return PlayerAnim.Root.NONE;
        float w = s.weight();
        return new PlayerAnim.Root(r.yaw() * w, r.pitch() * w, r.roll() * w, r.dy() * w, r.dz() * w);
    }

    public static void apply(HumanoidModel<?> model, EntityRenderState state) {
        Sample s = state.getRenderData(SAMPLE);
        if (s == null) return;
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
