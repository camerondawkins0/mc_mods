package com.slfrontier.client.overlay;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.slfrontier.SLFrontier;
import com.slfrontier.SlfConfig;
import com.slfrontier.client.ClientState;
import com.slfrontier.client.fx.FxRenderer;
import com.slfrontier.client.fx.WorldLayer;
import com.slfrontier.client.ui.Gfx;
import com.slfrontier.client.ui.Theme;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.world.Frontier;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Locale;

/**
 * Marks the current objective. In the world: a slowly turning, bobbing gold diamond over the
 * spot with a soft glow and a distance label, plus a tall light beam that reads from far away
 * and fades as you arrive. On screen: a pointer over the objective that slides to the screen
 * edge, with an arrow, when the objective is off-screen or behind the camera.
 */
public final class QuestPointer {
    private static final Identifier MARKER = Theme.icon("quest_marker");
    private static final Identifier EDGE_ARROW = Theme.icon("quest_edge_arrow");
    /** Flat-coloured, blended, back-face culled and depth-writing (registered in ClientSetup). */
    public static final RenderPipeline SOLID_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(SLFrontier.id("pipeline/quest_marker"))
            .withCull(true)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();
    /** Opaque, back-face culled, depth-writing geometry: the diamond itself. */
    private static final RenderType SOLID = RenderType.create("slfrontier_quest_marker", RenderSetup.builder(SOLID_PIPELINE).createRenderSetup());
    private static final int GOLD_LIGHT = 0xFFFFF1B0, GOLD = 0xFFF2B530, GOLD_DARK = 0xFFA86A08;
    private static final float HOVER = 2.3f;
    /** The on-screen pointer fades out between these distances as the world marker takes over. */
    private static final float HUD_FADE_NEAR = 14, HUD_FADE_FAR = 32;
    /** The beam fades in between these distances. */
    private static final float BEAM_NEAR = 10, BEAM_FAR = 40;
    /** The world distance label shows inside this distance. */
    private static final float LABEL_RANGE = 48;
    private static final int EDGE_MARGIN = 22;

    /** The marker's place this frame, taken while the level's render state is extracted. */
    private record Frame(double dx, double dz, double dist, double hoverY, double groundY, float size, float time,
                         Quaternionf rotation, float far) {
    }

    private static final ContextKey<Frame> FRAME = new ContextKey<>(SLFrontier.id("quest_pointer"));
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static boolean captured;

    @Nullable
    private static BlockPos anchorFor;
    private static double anchorY;
    private static int anchorAge;
    private static float clock;

    private QuestPointer() {
    }

    @Nullable
    private static ModNetwork.Marker target() {
        Minecraft mc = Minecraft.getInstance();
        if (!SlfConfig.QUEST_POINTER.get() || mc.player == null || mc.level == null || !Frontier.isIn(mc.player)) return null;
        return ClientState.questMarker();
    }

    public static void tick() {
        clock++;
        ModNetwork.Marker m = target();
        ClientLevel level = Minecraft.getInstance().level;
        if (m == null || level == null) {
            anchorFor = null;
            return;
        }
        if (!m.pos().equals(anchorFor) || ++anchorAge >= 20) {
            anchorFor = m.pos();
            anchorAge = 0;
            anchorY = groundY(level, m.pos());
        }
    }

    public static void clear() {
        anchorFor = null;
        captured = false;
    }

    /**
     * Where the marker stands. Objective positions are often rough (a planned town site or a
     * located structure), so unless the spot is somewhere you can stand, use the surface there.
     */
    private static double groundY(ClientLevel level, BlockPos pos) {
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return Math.max(pos.getY(), level.getSeaLevel());
        boolean standable = pos.getY() > level.getMinY()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
        return standable ? pos.getY() : level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    /**
     * Keeps this frame's camera matrices so the HUD can project the objective onto the screen,
     * including the view bob and damage tilt the level is drawn with.
     */
    private static void capture(CameraRenderState camera) {
        PoseStack bob = new PoseStack();
        bob(camera.entityRenderState, bob);
        VIEW.set(camera.viewRotationMatrix);
        PROJECTION.set(camera.projectionMatrix).mul(bob.last().pose());
        cameraPos = camera.pos;
        captured = true;
    }

    /** Vanilla's damage tilt and view bob (GameRenderer#bobHurt and #bobView), which it applies to the level's projection. */
    private static void bob(CameraEntityRenderState e, PoseStack pose) {
        Options options = Minecraft.getInstance().options;
        if (e.isLiving) {
            if (e.isDeadOrDying) pose.mulPose(Axis.ZP.rotationDegrees(40f - 8000f / (Math.min(e.deathTime, 20f) + 200f)));
            if (e.hurtTime >= 0 && !e.preventFlinch) {
                float hurt = e.hurtTime / e.hurtDuration;
                hurt = Mth.sin(hurt * hurt * hurt * hurt * Mth.PI);
                pose.mulPose(Axis.YP.rotationDegrees(-e.hurtDir));
                pose.mulPose(Axis.ZP.rotationDegrees((float) (-hurt * 14.0 * options.damageTiltStrength().get())));
                pose.mulPose(Axis.YP.rotationDegrees(e.hurtDir));
            }
        }
        if (e.isPlayer && options.bobView().get()) {
            float walk = e.backwardsInterpolatedWalkDistance * Mth.PI, b = e.bob;
            pose.translate(Mth.sin(walk) * b * 0.5f, -Math.abs(Mth.cos(walk) * b), 0f);
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(walk) * b * 3f));
            pose.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(walk - 0.2f) * b) * 5f));
        }
    }

    // ---- World ---------------------------------------------------------------------------

    public static void extract(LevelRenderState state, Camera camera, float pt) {
        capture(state.cameraRenderState);
        ModNetwork.Marker m = target();
        Minecraft mc = Minecraft.getInstance();
        if (m == null || anchorFor == null || mc.gui.hud.isHidden()) return;
        Vec3 cam = camera.position();
        double dx = m.pos().getX() + 0.5 - cam.x, dz = m.pos().getZ() + 0.5 - cam.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float time = clock + pt;
        float size = Math.min(4f, 1f + (float) Math.max(0, dist - 20) * 0.025f);
        double hoverY = anchorY + HOVER + Mth.sin(time * 0.07f) * 0.2f - cam.y;
        state.setRenderData(FRAME, new Frame(dx, dz, dist, hoverY, anchorY - cam.y, size, time, new Quaternionf(camera.rotation()),
                state.cameraRenderState.depthFar * 0.8f));
    }

    public static void submit(LevelRenderState state, SubmitNodeCollector collector, PoseStack pose) {
        Frame f = state.getRenderData(FRAME);
        if (f == null) return;
        double dx = f.dx, dz = f.dz, dist = f.dist, hoverY = f.hoverY;
        float size = f.size, time = f.time;

        float near = (float) Mth.clamp((Math.sqrt(dist * dist + hoverY * hoverY) - 1.2) / 2.0, 0.25, 1.0);
        WorldLayer.geometry(collector, WorldLayer.MARKER, pose, SOLID,
                (p, v) -> diamond(v, p.pose(), dx, hoverY, dz, size, time * 2.2f * Mth.DEG_TO_RAD, near));

        float beam = smoothstep(BEAM_NEAR, BEAM_FAR, (float) dist);
        WorldLayer.geometry(collector, WorldLayer.MARKER_GLOW, pose, FxRenderer.ADDITIVE, (p, v) -> {
            Matrix4f mat = p.pose();
            halo(v, mat, f.rotation, dx, hoverY, dz, 1.4f * size, near * (0.32f + 0.06f * Mth.sin(time * 0.15f)));
            if (beam > 0.01f) beam(v, mat, dx, f.groundY - 1, dz, dist, beam, time, f.far);
            if (dist < LABEL_RANGE) groundPulse(v, mat, dx, f.groundY + 0.05, dz, time);
        });

        float label = Mth.clamp((LABEL_RANGE - (float) dist) / 16f, 0f, 1f) * Mth.clamp(((float) dist - 1.5f) / 1.5f, 0f, 1f);
        if (label > 0.05f) {
            Font font = Minecraft.getInstance().font;
            Component text = distance(dist);
            pose.pushPose();
            pose.translate(dx, hoverY - 0.75 * size - 0.35, dz);
            pose.mulPose(f.rotation);
            float k = 0.025f * Math.max(1f, (float) dist / 10f);
            pose.scale(k, -k, k);
            float x = -font.width(text) / 2f;
            int a = (int) (label * 255);
            WorldLayer.text(collector, pose, x, 0, text, false, Font.DisplayMode.SEE_THROUGH, ((a / 3) << 24) | 0xFFE9A8,
                    (int) (label * 0x50) << 24);
            WorldLayer.text(collector, pose, x, 0, text, false, Font.DisplayMode.NORMAL, (a << 24) | 0xFFF1B0, 0);
            pose.popPose();
        }
    }

    /** An octahedron turning about its vertical axis, lit from above. Faces wind outward for culling. */
    private static void diamond(VertexConsumer v, Matrix4f m, double x, double y, double z, float size, float rot, float alpha) {
        float h = 0.55f * size, r = 0.36f * size;
        Vec3 top = new Vec3(x, y + h, z), bottom = new Vec3(x, y - h, z);
        Vec3[] ring = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            float a = rot + i * Mth.HALF_PI;
            ring[i] = new Vec3(x + Mth.cos(a) * r, y, z + Mth.sin(a) * r);
        }
        Vec3 light = new Vec3(0.4, 1, 0.3).normalize();
        for (int i = 0; i < 4; i++) {
            Vec3 a = ring[i], b = ring[(i + 1) % 4];
            float lit = shade(top, b, a, light);
            tri(v, m, top, withAlpha(Gfx.lerpColor(GOLD_LIGHT, 0xFFFFFFFF, lit * 0.5f), alpha),
                    b, withAlpha(Gfx.lerpColor(GOLD, GOLD_LIGHT, lit), alpha), a, withAlpha(Gfx.lerpColor(GOLD, GOLD_LIGHT, lit), alpha));
            lit = shade(bottom, a, b, light);
            tri(v, m, bottom, withAlpha(GOLD_DARK, alpha), a, withAlpha(Gfx.lerpColor(GOLD_DARK, GOLD, 0.4f + lit), alpha),
                    b, withAlpha(Gfx.lerpColor(GOLD_DARK, GOLD, 0.4f + lit), alpha));
        }
    }

    private static float shade(Vec3 a, Vec3 b, Vec3 c, Vec3 light) {
        Vec3 n = b.subtract(a).cross(c.subtract(a)).normalize();
        return (float) Mth.clamp(n.dot(light), 0, 1);
    }

    /** A soft camera-facing disc of light. */
    private static void halo(VertexConsumer v, Matrix4f m, Quaternionf rot, double cx, double cy, double cz, float radius, float alpha) {
        int centre = withAlpha(0xFFFFD34F, alpha), edge = withAlpha(0xFFFFD34F, 0f);
        // The camera's right and up axes span the disc.
        Vector3f right = rot.transform(new Vector3f(radius, 0, 0)), up = rot.transform(new Vector3f(0, radius, 0));
        int n = 20;
        for (int i = 0; i < n; i++) {
            float t0 = Mth.TWO_PI * i / n, t1 = Mth.TWO_PI * (i + 1) / n;
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            quad(v, m, cx, cy, cz, centre, cx, cy, cz, centre,
                    cx + right.x() * c1 + up.x() * s1, cy + right.y() * c1 + up.y() * s1, cz + right.z() * c1 + up.z() * s1, edge,
                    cx + right.x() * c0 + up.x() * s0, cy + right.y() * c0 + up.y() * s0, cz + right.z() * c0 + up.z() * s0, edge);
        }
    }

    /**
     * A tall column of light facing the camera, brightest in the middle and shimmering upward.
     * Beams beyond the far plane are pulled in along the line of sight and shrunk to match, so
     * they keep the same size on screen.
     */
    private static void beam(VertexConsumer v, Matrix4f m, double dx, double y0, double dz, double dist, float alpha, float time, float far) {
        float s = dist > far ? (float) (far / dist) : 1f;
        double x = dx * s, z = dz * s, base = y0 * s;
        float height = 240 * s;
        float width = (float) (0.35 + dist * 0.012) * s;
        double len = Math.sqrt(x * x + z * z);
        if (len < 1e-3) return;
        double sideX = -z / len, sideZ = x / len;
        int segments = 14;
        for (int i = 0; i < segments; i++) {
            float f0 = (float) i / segments, f1 = (float) (i + 1) / segments;
            float a0 = alpha * profile(f0, time), a1 = alpha * profile(f1, time);
            double ya = base + height * f0, yb = base + height * f1;
            for (int layer = 0; layer < 2; layer++) {
                float half = layer == 0 ? width * 2.2f : width * 0.45f;
                int color = layer == 0 ? 0xFFF2B530 : 0xFFFFF4C8;
                float strength = layer == 0 ? 0.22f : 0.6f;
                double ox = sideX * half, oz = sideZ * half;
                int c0 = withAlpha(color, a0 * strength), c1 = withAlpha(color, a1 * strength);
                int e0 = withAlpha(color, 0f);
                quad(v, m, x - ox, ya, z - oz, e0, x, ya, z, c0, x, yb, z, c1, x - ox, yb, z - oz, e0);
                quad(v, m, x, ya, z, c0, x + ox, ya, z + oz, e0, x + ox, yb, z + oz, e0, x, yb, z, c1);
            }
        }
    }

    /** Beam brightness along its height: rises from the ground, fades at the top, with bands drifting upward. */
    private static float profile(float f, float time) {
        float rise = Mth.clamp(f / 0.04f, 0f, 1f);
        float fall = 1f - smoothstep(0.35f, 1f, f);
        return rise * fall * (0.8f + 0.2f * Mth.sin(f * 40f - time * 0.12f));
    }

    /** A ring expanding from the base every two seconds, so the spot itself is easy to find. */
    private static void groundPulse(VertexConsumer v, Matrix4f m, double x, double y, double z, float time) {
        float phase = (time % 40f) / 40f;
        float r = 0.4f + phase * 2.6f, w = 0.18f;
        int col = withAlpha(0xFFFFD34F, (1 - phase) * 0.55f), clear = withAlpha(0xFFFFD34F, 0f);
        double ro = r + w, ri = Math.max(0, r - w);
        int n = 32;
        for (int i = 0; i < n; i++) {
            double t0 = Math.PI * 2 * i / n, t1 = Math.PI * 2 * (i + 1) / n;
            double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
            quad(v, m, x + c0 * ro, y, z + s0 * ro, clear, x + c0 * r, y, z + s0 * r, col, x + c1 * r, y, z + s1 * r, col,
                    x + c1 * ro, y, z + s1 * ro, clear);
            quad(v, m, x + c0 * r, y, z + s0 * r, col, x + c0 * ri, y, z + s0 * ri, clear, x + c1 * ri, y, z + s1 * ri, clear,
                    x + c1 * r, y, z + s1 * r, col);
        }
    }

    // ---- Screen --------------------------------------------------------------------------

    /** The on-screen pointer, drawn as a HUD layer; hidden while any screen is open. */
    public static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        ModNetwork.Marker m = target();
        if (m == null || anchorFor == null || !captured || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        double wx = m.pos().getX() + 0.5, wz = m.pos().getZ() + 0.5;
        double dx = wx - cameraPos.x, dz = wz - cameraPos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 3) return;
        float size = Math.min(4f, 1f + (float) Math.max(0, dist - 20) * 0.025f);
        Vector4f view = new Vector4f((float) dx, (float) (anchorY + HOVER + 0.9f * size - cameraPos.y), (float) dz, 1f);
        VIEW.transform(view);
        Vector4f clip = PROJECTION.transform(new Vector4f(view));
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h / 2f;
        boolean inFront = clip.w() > 0.05f;
        float sx = 0, sy = 0;
        if (inFront) {
            sx = (clip.x() / clip.w() * 0.5f + 0.5f) * w;
            sy = (0.5f - clip.y() / clip.w() * 0.5f) * h;
        }
        Font font = mc.font;
        Component name = Component.translatable(m.nameKey());
        if (inFront && sx >= EDGE_MARGIN && sx <= w - EDGE_MARGIN && sy >= EDGE_MARGIN && sy <= h - EDGE_MARGIN) {
            Gfx.alpha = smoothstep(HUD_FADE_NEAR, HUD_FADE_FAR, (float) dist);
            if (Gfx.alpha > 0.03f) {
                float pulse = 1f + 0.06f * Mth.sin((clock + delta.getGameTimeDeltaPartialTick(false)) * 0.2f);
                sprite(g, MARKER, sx, sy - 6, 16 * pulse, 0f);
                Component d = distance(dist);
                Gfx.text(g, font, d, sx - font.width(d) * 0.4f, sy + 4, 0.8f, 0xFFFFFFFF, true);
                Gfx.text(g, font, name, sx - font.width(name) * 0.3f, sy + 12, 0.6f, Theme.GOLD, true);
            }
        } else {
            float ex = inFront ? sx - cx : view.x(), ey = inFront ? sy - cy : -view.y();
            if (Math.abs(ex) + Math.abs(ey) < 1e-3f) ey = 1f;
            float hx = cx - EDGE_MARGIN, hy = cy - EDGE_MARGIN;
            float t = Math.min(hx / Math.max(1e-3f, Math.abs(ex)), hy / Math.max(1e-3f, Math.abs(ey)));
            float px = cx + ex * t, py = cy + ey * t;
            float angle = (float) Math.atan2(ey, ex);
            float ux = Mth.cos(angle), uy = Mth.sin(angle);
            Gfx.alpha = 1f;
            sprite(g, EDGE_ARROW, px + ux * 11, py + uy * 11, 14, angle + Mth.HALF_PI);
            sprite(g, MARKER, px, py, 14, 0f);
            Component d = distance(dist);
            float tw = font.width(d) * 0.75f;
            float lx = px - ux * 16 - tw / 2, ly = py - uy * 14 - 3;
            Gfx.text(g, font, d, Mth.clamp(lx, 2, w - tw - 2), Mth.clamp(ly, 2, h - 9), 0.75f, 0xFFFFFFFF, true);
        }
        Gfx.alpha = 1f;
    }

    private static void sprite(GuiGraphicsExtractor g, Identifier tex, float x, float y, float size, float rot) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        if (rot != 0) g.pose().rotate(rot);
        g.pose().scale(size / 32f, size / 32f);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, -16, -16, 0f, 0f, 32, 32, 32, 32, ARGB.white(Mth.clamp(Gfx.alpha, 0f, 1f)));
        g.pose().popMatrix();
    }

    // ---- Helpers -------------------------------------------------------------------------

    /** "850m", or "1.1km" from a kilometre on: the one distance format of the HUD, pointer and map. */
    public static Component distance(double meters) {
        if (meters < 1000) return Component.translatable("pointer.slfrontier.meters", (int) meters);
        return Component.translatable("pointer.slfrontier.kilometers", String.format(Locale.ROOT, "%.1f", meters / 1000));
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private static int withAlpha(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * Mth.clamp(alpha, 0f, 1f));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static void tri(VertexConsumer v, Matrix4f m, Vec3 a, int ca, Vec3 b, int cb, Vec3 c, int cc) {
        quad(v, m, a, ca, b, cb, c, cc, c, cc);
    }

    /** A quad from plain coordinates: the beam, halo and ground ring draw hundreds a frame, so no Vec3s. */
    private static void quad(VertexConsumer v, Matrix4f m, double ax, double ay, double az, int ca, double bx, double by, double bz, int cb,
                             double cx, double cy, double cz, int cc, double dx, double dy, double dz, int cd) {
        v.addVertex(m, (float) ax, (float) ay, (float) az).setColor(ca);
        v.addVertex(m, (float) bx, (float) by, (float) bz).setColor(cb);
        v.addVertex(m, (float) cx, (float) cy, (float) cz).setColor(cc);
        v.addVertex(m, (float) dx, (float) dy, (float) dz).setColor(cd);
    }

    private static void quad(VertexConsumer v, Matrix4f m, Vec3 a, int ca, Vec3 b, int cb, Vec3 c, int cc, Vec3 d, int cd) {
        v.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(ca);
        v.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(cb);
        v.addVertex(m, (float) c.x, (float) c.y, (float) c.z).setColor(cc);
        v.addVertex(m, (float) d.x, (float) d.y, (float) d.z).setColor(cd);
    }
}
