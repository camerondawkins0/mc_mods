package com.slfrontier.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.slfrontier.SlfConfig;
import com.slfrontier.fx.Fx;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Draws the server's {@link Fx} effects in the world after particles. */
public final class FxRenderer {
    /** Glowing, additive, no depth write: slashes, rings, spirals. */
    public static final RenderType ADDITIVE = RenderType.create("slfrontier_fx_additive", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 8192, false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));
    /** Alpha-blended ground decals: boss telegraphs. */
    public static final RenderType DECAL = RenderType.create("slfrontier_fx_decal", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private record Instance(Fx.Spec spec, float start) {
    }

    private static final List<Instance> ACTIVE = new ArrayList<>();
    private static float clock;

    private FxRenderer() {
    }

    public static void add(Fx.Spec spec) {
        ACTIVE.add(new Instance(spec, clock));
        var player = Minecraft.getInstance().player;
        if (spec.shake() > 0 && player != null && SlfConfig.CAMERA_SHAKE.get()) {
            double d = player.position().distanceTo(new Vec3(spec.x(), spec.y(), spec.z()));
            float falloff = (float) Mth.clamp(1 - d / 32.0, 0, 1);
            if (falloff > 0) CameraShake.add(spec.shake() * falloff, Math.max(6, spec.duration() / 2));
        }
    }

    public static void tick() {
        clock++;
        ACTIVE.removeIf(i -> clock - i.start > i.spec.duration() + 1);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static int count() {
        return ACTIVE.size();
    }

    public static void render(Camera camera, float partialTick) {
        if (ACTIVE.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer decal = buffers.getBuffer(DECAL);
        for (Instance i : ACTIVE) {
            int kind = i.spec.kind();
            if (kind == Fx.TELEGRAPH || kind == Fx.CONE) draw(i, decal, m, cam, camera, partialTick);
        }
        buffers.endBatch(DECAL);
        VertexConsumer add = buffers.getBuffer(ADDITIVE);
        for (Instance i : ACTIVE) {
            int kind = i.spec.kind();
            if (kind != Fx.TELEGRAPH && kind != Fx.CONE) draw(i, add, m, cam, camera, partialTick);
        }
        buffers.endBatch(ADDITIVE);
    }

    private static void draw(Instance inst, VertexConsumer v, Matrix4f m, Vec3 cam, Camera camera, float pt) {
        Fx.Spec s = inst.spec;
        float age = clock + pt - inst.start;
        float life = Mth.clamp(age / Math.max(1, s.duration()), 0f, 1f);
        Vec3 c = new Vec3(s.x() - cam.x, s.y() - cam.y, s.z() - cam.z);
        switch (s.kind()) {
            case Fx.ARC -> arc(v, m, c, s, life, Math.min(1f, life / 0.35f), true);
            case Fx.WAVE -> {
                double yaw = Math.toRadians(s.yaw());
                double dist = s.param() * easeOut(life);
                Vec3 at = c.add(-Math.sin(yaw) * dist, 0, Math.cos(yaw) * dist);
                arc(v, m, at, s, life, 1f, false);
            }
            case Fx.RING -> ring(v, m, c, s, life);
            case Fx.SPIRAL -> spiral(v, m, c, s, life, age);
            case Fx.PILLAR -> pillar(v, m, c, s, life);
            case Fx.TELEGRAPH -> telegraph(v, m, c, s, life);
            case Fx.CONE -> cone(v, m, c, s, life);
            case Fx.FLASH -> flash(v, m, c, s, life, camera);
            case Fx.BEAM -> beam(v, m, c, s, life);
            default -> {
            }
        }
    }

    private static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private static int argb(int color, float alpha) {
        int a = (int) (((color >>> 24) & 0xFF) * Mth.clamp(alpha, 0f, 1f));
        return (a << 24) | (color & 0xFFFFFF);
    }

    private static void quad(VertexConsumer v, Matrix4f m, Vec3 a, int ca, Vec3 b, int cb, Vec3 c, int cc, Vec3 d, int cd) {
        v.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(ca);
        v.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(cb);
        v.addVertex(m, (float) c.x, (float) c.y, (float) c.z).setColor(cc);
        v.addVertex(m, (float) d.x, (float) d.y, (float) d.z).setColor(cd);
    }

    /** Basis vectors of an arc's plane: forward and the tilted sideways axis. */
    private static Vec3[] basis(float yawDeg, float tiltDeg) {
        double yaw = Math.toRadians(yawDeg), tilt = Math.toRadians(tiltDeg);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 side = right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
        return new Vec3[]{fwd, side};
    }

    /**
     * A crescent ribbon. {@code sweep} 0..1 is how much of the arc has been drawn; the head
     * is bright and the tail fades, like a blade trail.
     */
    private static void arc(VertexConsumer v, Matrix4f m, Vec3 c, Fx.Spec s, float life, float sweep, boolean trail) {
        Vec3[] b = basis(s.yaw(), s.pitch());
        float span = (float) Math.toRadians(s.param() <= 0 ? 160 : s.param());
        float a0 = -span / 2;
        int n = 28;
        float fade = life < 0.35f ? 1f : 1f - (life - 0.35f) / 0.65f;
        float r = s.radius();
        for (int layer = 0; layer < 2; layer++) {
            float width = layer == 0 ? r * 0.22f : r * 0.07f;
            int color = layer == 0 ? s.color() : 0xFFFFFFFF;
            float layerAlpha = layer == 0 ? 0.8f : 0.9f;
            for (int i = 0; i < n; i++) {
                float f0 = (float) i / n * sweep, f1 = (float) (i + 1) / n * sweep;
                float t0 = a0 + span * f0, t1 = a0 + span * f1;
                // Thicker and brighter toward the leading edge.
                float h0 = trail ? f0 / Math.max(0.01f, sweep) : 1f - Math.abs(f0 - 0.5f) * 1.6f;
                float h1 = trail ? f1 / Math.max(0.01f, sweep) : 1f - Math.abs(f1 - 0.5f) * 1.6f;
                float w0 = width * (0.35f + 0.65f * h0), w1 = width * (0.35f + 0.65f * h1);
                Vec3 d0 = b[0].scale(Math.cos(t0)).add(b[1].scale(Math.sin(t0)));
                Vec3 d1 = b[0].scale(Math.cos(t1)).add(b[1].scale(Math.sin(t1)));
                int c0 = argb(color, layerAlpha * fade * h0), c1 = argb(color, layerAlpha * fade * h1);
                int e0 = argb(color, 0), e1 = argb(color, 0);
                Vec3 mid0 = c.add(d0.scale(r)), mid1 = c.add(d1.scale(r));
                // Two strips: soft outer edge -> bright centre line -> soft inner edge.
                quad(v, m, c.add(d0.scale(r + w0)), e0, mid0, c0, mid1, c1, c.add(d1.scale(r + w1)), e1);
                quad(v, m, mid0, c0, c.add(d0.scale(r - w0)), e0, c.add(d1.scale(r - w1)), e1, mid1, c1);
            }
        }
    }

    private static void ring(VertexConsumer v, Matrix4f m, Vec3 c, Fx.Spec s, float life) {
        int n = 48;
        for (int layer = 0; layer < 2; layer++) {
            float lt = layer == 0 ? life : Mth.clamp(life * 1.4f - 0.2f, 0f, 1f);
            float r = s.radius() * (0.15f + 0.85f * easeOut(lt));
            float w = s.radius() * 0.14f * (1 - lt) + 0.06f;
            float a = (1 - lt) * (layer == 0 ? 0.9f : 0.6f);
            int col = argb(layer == 0 ? s.color() : 0xFFFFFFFF, a), clear = argb(s.color(), 0);
            for (int i = 0; i < n; i++) {
                double t0 = Math.PI * 2 * i / n, t1 = Math.PI * 2 * (i + 1) / n;
                Vec3 o0 = new Vec3(Math.cos(t0), 0, Math.sin(t0)), o1 = new Vec3(Math.cos(t1), 0, Math.sin(t1));
                quad(v, m, c.add(o0.scale(r + w)), clear, c.add(o0.scale(r)), col, c.add(o1.scale(r)), col, c.add(o1.scale(r + w)), clear);
                quad(v, m, c.add(o0.scale(r)), col, c.add(o0.scale(Math.max(0, r - w))), clear, c.add(o1.scale(Math.max(0, r - w))), clear,
                        c.add(o1.scale(r)), col);
                // A low glowing wall so the wave reads from a distance.
                Vec3 up = new Vec3(0, w * 1.5, 0);
                quad(v, m, c.add(o0.scale(r)), col, c.add(o0.scale(r)).add(up), clear, c.add(o1.scale(r)).add(up), clear, c.add(o1.scale(r)), col);
            }
        }
    }

    private static void spiral(VertexConsumer v, Matrix4f m, Vec3 c, Fx.Spec s, float life, float age) {
        double yaw = Math.toRadians(s.yaw()), pitch = Math.toRadians(s.pitch());
        Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 side = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = dir.cross(side).normalize();
        float len = s.radius() * easeOut(Math.min(1, life * 2.5f));
        float fade = life < 0.5f ? 1 : 1 - (life - 0.5f) * 2;
        int strands = 3, n = 40;
        for (int k = 0; k < strands; k++) {
            for (int i = 0; i < n; i++) {
                float f0 = (float) i / n, f1 = (float) (i + 1) / n;
                double a0 = f0 * 9 + age * 0.8 + k * Math.PI * 2 / strands, a1 = f1 * 9 + age * 0.8 + k * Math.PI * 2 / strands;
                float rad0 = 0.15f + 0.45f * f0, rad1 = 0.15f + 0.45f * f1;
                Vec3 p0 = c.add(dir.scale(len * f0)).add(side.scale(Math.cos(a0) * rad0)).add(up.scale(Math.sin(a0) * rad0));
                Vec3 p1 = c.add(dir.scale(len * f1)).add(side.scale(Math.cos(a1) * rad1)).add(up.scale(Math.sin(a1) * rad1));
                Vec3 w = up.scale(0.07);
                int col0 = argb(s.color(), fade * (0.4f + 0.6f * f0)), col1 = argb(s.color(), fade * (0.4f + 0.6f * f1));
                quad(v, m, p0.add(w), col0, p0.subtract(w), col0, p1.subtract(w), col1, p1.add(w), col1);
            }
        }
        // Core beam
        Vec3 w = up.scale(0.05);
        int core = argb(0xFFFFFFFF, fade * 0.7f);
        quad(v, m, c.add(w), core, c.subtract(w), core, c.add(dir.scale(len)).subtract(w), argb(0xFFFFFFFF, 0), c.add(dir.scale(len)).add(w),
                argb(0xFFFFFFFF, 0));
    }

    /** Two crossed glowing strips along a direction, with a white-hot core; it shoots out, then fades. */
    private static void beam(VertexConsumer v, Matrix4f m, Vec3 c, Fx.Spec s, float life) {
        double yaw = Math.toRadians(s.yaw()), pitch = Math.toRadians(s.pitch());
        Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 side = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = dir.cross(side).normalize();
        float len = s.param() * easeOut(Math.min(1, life * 4));
        float fade = life < 0.4f ? 1 : 1 - (life - 0.4f) / 0.6f;
        float w = s.radius() * (1 + 0.15f * Mth.sin(life * 40));
        Vec3 end = c.add(dir.scale(len));
        for (Vec3 axis : new Vec3[]{side, up}) {
            Vec3 o = axis.scale(w);
            int edge = argb(s.color(), 0), mid = argb(s.color(), fade * 0.9f);
            quad(v, m, c.add(o), edge, c, mid, end, mid, end.add(o), edge);
            quad(v, m, c, mid, c.subtract(o), edge, end.subtract(o), edge, end, mid);
            Vec3 k = axis.scale(w * 0.3);
            int core = argb(0xFFFFFFFF, fade * 0.8f);
            quad(v, m, c.add(k), core, c.subtract(k), core, end.subtract(k), core, end.add(k), core);
        }
    }

    private static void pillar(VertexConsumer v, Matrix4f m, Vec3 c, Fx.Spec s, float life) {
        int n = 20;
        float r = s.radius() * (0.6f + 0.4f * easeOut(life));
        float h = s.param() * easeOut(Math.min(1, life * 3));
        float a = life < 0.3f ? 1 : 1 - (life - 0.3f) / 0.7f;
        int base = argb(s.color(), a * 0.8f), top = argb(s.color(), 0);
        for (int i = 0; i < n; i++) {
            double t0 = Math.PI * 2 * i / n, t1 = Math.PI * 2 * (i + 1) / n;
            Vec3 o0 = new Vec3(Math.cos(t0) * r, 0, Math.sin(t0) * r), o1 = new Vec3(Math.cos(t1) * r, 0, Math.sin(t1) * r);
            Vec3 hv = new Vec3(0, h, 0);
            quad(v, m, c.add(o0), base, c.add(o0).add(hv), top, c.add(o1).add(hv), top, c.add(o1), base);
            Vec3 i0 = o0.scale(0.35), i1 = o1.scale(0.35);
            int core = argb(0xFFFFFFFF, a * 0.8f);
            quad(v, m, c.add(i0), core, c.add(i0).add(hv), top, c.add(i1).add(hv), top, c.add(i1), core);
        }
    }

    private static void disc(VertexConsumer v, Matrix4f m, Vec3 c, float r, float from, float to, int inner, int outer, int n) {
        for (int i = 0; i < n; i++) {
            double t0 = from + (to - from) * i / n, t1 = from + (to - from) * (i + 1) / n;
            quad(v, m, c, inner, c, inner, c.add(Math.cos(t1) * r, 0, Math.sin(t1) * r), outer, c.add(Math.cos(t0) * r, 0, Math.sin(t0) * r), outer);
        }
    }

    private static void band(VertexConsumer v, Matrix4f m, Vec3 c, float r0, float r1, float from, float to, int color, int n) {
        for (int i = 0; i < n; i++) {
            double t0 = from + (to - from) * i / n, t1 = from + (to - from) * (i + 1) / n;
            quad(v, m, c.add(Math.cos(t0) * r0, 0, Math.sin(t0) * r0), color, c.add(Math.cos(t0) * r1, 0, Math.sin(t0) * r1), color,
                    c.add(Math.cos(t1) * r1, 0, Math.sin(t1) * r1), color, c.add(Math.cos(t1) * r0, 0, Math.sin(t1) * r0), color);
        }
    }

    /** A danger circle: faint zone, a fill that grows until impact, and a bright rim. */
    private static void telegraph(VertexConsumer v, Matrix4f m, Vec3 c, Fx.Spec s, float life) {
        float r = s.radius();
        float pulse = 0.75f + 0.25f * Mth.sin(life * 30);
        disc(v, m, c, r, 0, Mth.TWO_PI, argb(s.color(), 0.10f), argb(s.color(), 0.22f), 40);
        disc(v, m, c.add(0, 0.01, 0), r * life, 0, Mth.TWO_PI, argb(s.color(), 0.30f), argb(s.color(), 0.45f), 40);
        band(v, m, c.add(0, 0.02, 0), r - 0.12f, r, 0, Mth.TWO_PI, argb(s.color(), 0.85f * pulse), 48);
    }

    private static void cone(VertexConsumer v, Matrix4f m, Vec3 c, Fx.Spec s, float life) {
        float half = (float) Math.toRadians(s.param() / 2);
        // Minecraft yaw 0 faces +Z; convert to the angle used by cos/sin on the XZ plane.
        float center = (float) Math.toRadians(s.yaw() + 90);
        float r = s.radius();
        disc(v, m, c, r, center - half, center + half, argb(s.color(), 0.10f), argb(s.color(), 0.25f), 24);
        disc(v, m, c.add(0, 0.01, 0), r * life, center - half, center + half, argb(s.color(), 0.30f), argb(s.color(), 0.5f), 24);
        band(v, m, c.add(0, 0.02, 0), r - 0.12f, r, center - half, center + half, argb(s.color(), 0.85f), 24);
    }

    private static void flash(VertexConsumer v, Matrix4f m, Vec3 c, Fx.Spec s, float life, Camera camera) {
        Quaternionf q = camera.rotation();
        // Keep flashes from swallowing the screen when they happen right at the camera.
        float dist = (float) c.length();
        float size = Math.min(s.radius(), Math.max(0.2f, dist * 0.35f)) * (0.4f + 0.9f * easeOut(life));
        float a = (1 - life) * (1 - life);
        int centre = argb(s.color(), a), edge = argb(s.color(), 0);
        int n = 16;
        for (int i = 0; i < n; i++) {
            double t0 = Math.PI * 2 * i / n, t1 = Math.PI * 2 * (i + 1) / n;
            Vector3f r0 = q.transform(new Vector3f((float) Math.cos(t0) * size, (float) Math.sin(t0) * size, 0));
            Vector3f r1 = q.transform(new Vector3f((float) Math.cos(t1) * size, (float) Math.sin(t1) * size, 0));
            quad(v, m, c, centre, c, centre, c.add(r1.x(), r1.y(), r1.z()), edge, c.add(r0.x(), r0.y(), r0.z()), edge);
        }
        // Four thin rays for a star-like glint.
        for (int k = 0; k < 4; k++) {
            double ang = Math.PI / 4 + k * Math.PI / 2;
            Vector3f tip = q.transform(new Vector3f((float) Math.cos(ang) * size * 1.8f, (float) Math.sin(ang) * size * 1.8f, 0));
            Vector3f w = q.transform(new Vector3f((float) -Math.sin(ang) * size * 0.08f, (float) Math.cos(ang) * size * 0.08f, 0));
            quad(v, m, c.add(w.x(), w.y(), w.z()), centre, c.subtract(w.x(), w.y(), w.z()), centre,
                    c.add(tip.x(), tip.y(), tip.z()), edge, c.add(tip.x(), tip.y(), tip.z()), edge);
        }
    }
}
