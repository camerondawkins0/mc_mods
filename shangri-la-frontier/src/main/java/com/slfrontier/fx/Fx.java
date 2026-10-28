package com.slfrontier.fx;

import com.slfrontier.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * World-space visual effects (slash arcs, shockwaves, spirals, telegraphs...) that the
 * server asks nearby clients to draw. Kinds are interpreted by the client's FxRenderer.
 */
public final class Fx {
    /** A sweeping crescent ribbon in a (possibly tilted) plane around a point. */
    public static final int ARC = 0;
    /** An expanding shockwave ring on the ground. */
    public static final int RING = 1;
    /** A drilling spiral along a direction (Drill Piercer / Glowing Pierce). */
    public static final int SPIRAL = 2;
    /** A vertical column of light. */
    public static final int PILLAR = 3;
    /** A circular danger zone on the ground that fills up until the hit lands. */
    public static final int TELEGRAPH = 4;
    /** A cone-shaped danger zone in front of the caster. */
    public static final int CONE = 5;
    /** A crescent that flies forward (Moon Reflection, Magic Edge, Tachikaze). */
    public static final int WAVE = 6;
    /** A bright impact flash facing the camera. */
    public static final int FLASH = 7;
    /** A straight glowing beam (lasers, magic bolts); param is its length. */
    public static final int BEAM = 8;

    /**
     * @param yaw    facing in degrees (Minecraft yRot)
     * @param pitch  plane tilt for arcs/waves (degrees, 0 = horizontal, 90 = vertical)
     * @param param  kind-specific: arc span (degrees), pillar height, cone angle, wave travel distance
     * @param shake  camera shake strength for nearby players (0 = none)
     */
    public record Spec(int kind, float x, float y, float z, float yaw, float pitch, float radius, int color, int duration,
                       float param, float shake) {
    }

    private Fx() {
    }

    public static void send(Level level, Spec spec) {
        if (level instanceof ServerLevel server) {
            PacketDistributor.sendToPlayersNear(server, null, spec.x(), spec.y(), spec.z(), 64, new ModNetwork.FxPayload(spec));
        }
    }

    private static Vec3 chest(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    /** A slash around an entity: {@code tilt} 0 = horizontal sweep, positive/negative = diagonal, 90 = vertical. */
    public static void arc(Entity e, float radius, float tilt, float span, int color, int duration) {
        Vec3 c = chest(e);
        send(e.level(), new Spec(ARC, (float) c.x, (float) c.y, (float) c.z, e.getYRot(), tilt, radius, color, duration, span, 0));
    }

    public static void ring(Level level, Vec3 at, float radius, int color, int duration, float shake) {
        send(level, new Spec(RING, (float) at.x, (float) at.y + 0.05f, (float) at.z, 0, 0, radius, color, duration, 0, shake));
    }

    public static void spiral(Entity e, Vec3 dir, float length, int color, int duration) {
        Vec3 c = e.getEyePosition().subtract(0, 0.35, 0);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, dir.y))));
        send(e.level(), new Spec(SPIRAL, (float) c.x, (float) c.y, (float) c.z, yaw, pitch, length, color, duration, 0, 0));
    }

    public static void pillar(Level level, Vec3 at, float radius, float height, int color, int duration) {
        send(level, new Spec(PILLAR, (float) at.x, (float) at.y, (float) at.z, 0, 0, radius, color, duration, height, 0));
    }

    public static void telegraph(Level level, Vec3 at, float radius, int color, int duration) {
        send(level, new Spec(TELEGRAPH, (float) at.x, (float) at.y + 0.06f, (float) at.z, 0, 0, radius, color, duration, 0, 0));
    }

    public static void cone(Entity e, float radius, float angle, int color, int duration) {
        send(e.level(), new Spec(CONE, (float) e.getX(), (float) e.getY() + 0.06f, (float) e.getZ(), e.getYRot(), 0, radius, color,
                duration, angle, 0));
    }

    public static void wave(Entity e, float radius, float tilt, float distance, int color, int duration) {
        Vec3 c = chest(e);
        send(e.level(), new Spec(WAVE, (float) c.x, (float) c.y, (float) c.z, e.getYRot(), tilt, radius, color, duration, distance, 0));
    }

    public static void flash(Level level, Vec3 at, float radius, int color, int duration, float shake) {
        send(level, new Spec(FLASH, (float) at.x, (float) at.y, (float) at.z, 0, 0, radius, color, duration, 0, shake));
    }

    /** Yaw (Minecraft convention) pointing from {@code from} toward {@code to}. */
    public static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    /** A cone-shaped danger zone at a point, aimed along {@code yaw}; a narrow cone reads as a line. */
    public static void coneAt(Level level, Vec3 at, float yaw, float radius, float angle, int color, int duration) {
        send(level, new Spec(CONE, (float) at.x, (float) at.y + 0.06f, (float) at.z, yaw, 0, radius, color, duration, angle, 0));
    }

    /** A slash ribbon at a point, facing {@code yaw}. */
    public static void arcAt(Level level, Vec3 at, float yaw, float radius, float tilt, float span, int color, int duration) {
        send(level, new Spec(ARC, (float) at.x, (float) at.y, (float) at.z, yaw, tilt, radius, color, duration, span, 0));
    }

    /** A flying crescent launched from a point along {@code yaw}. */
    public static void waveAt(Level level, Vec3 at, float yaw, float radius, float tilt, float distance, int color, int duration,
                              float shake) {
        send(level, new Spec(WAVE, (float) at.x, (float) at.y, (float) at.z, yaw, tilt, radius, color, duration, distance, shake));
    }

    /** A beam from {@code from} to {@code to}. */
    public static void beam(Level level, Vec3 from, Vec3 to, float width, int color, int duration) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-3) return;
        float yaw = yawTo(from, to);
        float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, d.y / len))));
        send(level, new Spec(BEAM, (float) from.x, (float) from.y, (float) from.z, yaw, pitch, width, color, duration, (float) len, 0));
    }
}
