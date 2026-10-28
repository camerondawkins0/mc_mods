package com.slfrontier.client.fx;

import net.minecraft.util.Mth;

/** Short, decaying camera shake for heavy hits and boss slams. */
public final class CameraShake {
    private static float strength;
    private static float remaining;
    private static float total;
    private static float clock;

    private CameraShake() {
    }

    public static void add(float amount, int ticks) {
        // Stronger shakes override weaker ones; overlapping ones don't stack endlessly.
        float current = total <= 0 ? 0 : strength * remaining / total;
        if (amount >= current) {
            strength = Math.min(amount, 3f);
            remaining = ticks;
            total = ticks;
        }
    }

    public static void tick() {
        clock++;
        if (remaining > 0) remaining--;
    }

    /** {yaw, pitch, roll} offsets in degrees for this frame. */
    public static float[] offsets(float partialTick) {
        if (remaining <= 0 || total <= 0) return null;
        float k = strength * Mth.clamp((remaining - partialTick) / total, 0f, 1f);
        float t = (clock + partialTick) * 2.3f;
        return new float[]{Mth.sin(t * 1.7f) * k, Mth.sin(t * 2.3f + 1.3f) * k, Mth.sin(t * 1.1f + 2.1f) * k * 0.6f};
    }
}
