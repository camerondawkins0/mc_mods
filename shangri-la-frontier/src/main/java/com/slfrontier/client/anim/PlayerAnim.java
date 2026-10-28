package com.slfrontier.client.anim;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A keyframed full-body animation for players (skills, slashes). Limb channels hold
 * absolute rotations in degrees; the ROOT channel moves the whole body (yaw spin, lean,
 * roll, height and forward offset). Time runs 0..1 over the animation's length.
 */
public final class PlayerAnim {
    public enum Part { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, ROOT }

    public enum Ease {
        LINEAR, IN, OUT, IN_OUT, BACK;

        float apply(float t) {
            return switch (this) {
                case LINEAR -> t;
                case IN -> t * t * t;
                case OUT -> 1 - (1 - t) * (1 - t) * (1 - t);
                case IN_OUT -> t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
                case BACK -> {
                    float c1 = 1.70158f, c3 = c1 + 1;
                    yield 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
                }
            };
        }
    }

    /** One keyframe: time 0..1, three values, and the easing used to arrive here. */
    private record Key(float t, float a, float b, float c, Ease ease) {
    }

    /** Root values: yaw spin, pitch lean (+forward), roll, height offset and forward offset (blocks). */
    public record Root(float yaw, float pitch, float roll, float dy, float dz) {
        public static final Root NONE = new Root(0, 0, 0, 0, 0);
    }

    public final int ticks;
    /** Ticks spent blending in from (and out to) the vanilla pose. */
    public final float blendIn, blendOut;
    private final Map<Part, List<Key>> channels = new EnumMap<>(Part.class);
    private final Map<Part, List<Key>> rootExtra = new EnumMap<>(Part.class);

    private PlayerAnim(int ticks, float blendIn, float blendOut) {
        this.ticks = ticks;
        this.blendIn = blendIn;
        this.blendOut = blendOut;
    }

    public static Builder of(int ticks) {
        return new Builder(ticks);
    }

    public boolean has(Part part) {
        return channels.containsKey(part);
    }

    /** Rotation (degrees) of a limb at time t, or null if this animation leaves it alone. */
    public float[] sample(Part part, float t) {
        List<Key> keys = channels.get(part);
        if (keys == null) return null;
        return interpolate(keys, t);
    }

    public Root root(float t) {
        List<Key> rot = channels.get(Part.ROOT);
        List<Key> off = rootExtra.get(Part.ROOT);
        float[] r = rot == null ? new float[3] : interpolate(rot, t);
        float[] o = off == null ? new float[3] : interpolate(off, t);
        return new Root(r[0], r[1], r[2], o[0], o[1]);
    }

    private static float[] interpolate(List<Key> keys, float t) {
        if (t <= keys.get(0).t()) return new float[]{keys.get(0).a(), keys.get(0).b(), keys.get(0).c()};
        for (int i = 1; i < keys.size(); i++) {
            Key k = keys.get(i);
            if (t <= k.t()) {
                Key p = keys.get(i - 1);
                float f = k.ease().apply((t - p.t()) / Math.max(1e-4f, k.t() - p.t()));
                return new float[]{Mth.lerp(f, p.a(), k.a()), Mth.lerp(f, p.b(), k.b()), Mth.lerp(f, p.c(), k.c())};
            }
        }
        Key last = keys.get(keys.size() - 1);
        return new float[]{last.a(), last.b(), last.c()};
    }

    /** 0..1 weight of the animation against the vanilla pose at an elapsed tick time. */
    public float weight(float elapsed) {
        float in = blendIn <= 0 ? 1 : Mth.clamp(elapsed / blendIn, 0f, 1f);
        float out = blendOut <= 0 ? 1 : Mth.clamp((ticks - elapsed) / blendOut, 0f, 1f);
        return Math.min(in, out);
    }

    public static final class Builder {
        private final PlayerAnim anim;
        private Ease ease = Ease.IN_OUT;

        private Builder(int ticks) {
            this.anim = new PlayerAnim(ticks, Math.min(2f, ticks / 4f), Math.min(3f, ticks / 3f));
        }

        private Builder(PlayerAnim anim) {
            this.anim = anim;
        }

        public Builder blend(float in, float out) {
            Builder b = new Builder(new PlayerAnim(anim.ticks, in, out));
            b.anim.channels.putAll(anim.channels);
            b.anim.rootExtra.putAll(anim.rootExtra);
            b.ease = ease;
            return b;
        }

        /** Easing for the keyframes added after this call. */
        public Builder ease(Ease ease) {
            this.ease = ease;
            return this;
        }

        /** Limb rotation keyframes as groups of (t, x, y, z) degrees. */
        public Builder rot(Part part, float... txyz) {
            List<Key> list = anim.channels.computeIfAbsent(part, k -> new ArrayList<>());
            for (int i = 0; i + 3 < txyz.length; i += 4) list.add(new Key(txyz[i], txyz[i + 1], txyz[i + 2], txyz[i + 3], ease));
            list.sort((a, b) -> Float.compare(a.t(), b.t()));
            return this;
        }

        /** Whole-body rotation keyframes as groups of (t, yaw, pitch, roll) degrees. */
        public Builder spin(float... tYawPitchRoll) {
            return rot(Part.ROOT, tYawPitchRoll);
        }

        /** Whole-body offsets as groups of (t, dy, dz, unused) in blocks. */
        public Builder move(float... tDyDz) {
            List<Key> list = anim.rootExtra.computeIfAbsent(Part.ROOT, k -> new ArrayList<>());
            for (int i = 0; i + 2 < tDyDz.length; i += 3) list.add(new Key(tDyDz[i], tDyDz[i + 1], tDyDz[i + 2], 0, ease));
            list.sort((a, b) -> Float.compare(a.t(), b.t()));
            return this;
        }

        public PlayerAnim build() {
            return anim;
        }
    }
}
