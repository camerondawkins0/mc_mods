package com.slfrontier.client.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immediate-mode drawing helpers for the Frontier UI: coloured polygons (chamfered panels,
 * gradients, pies, rings, lines), all drawn as GUI quads so they stay crisp at any GUI
 * scale. Colours are ARGB. {@link #alpha} fades everything drawn through here.
 *
 * <p>The GUI is extracted into a render state and drawn later, so quads are collected (already
 * transformed by the current pose) and {@link #flush flushed} into one {@link Shape} element per
 * primitive; the GUI renderer keeps elements in submission order where they overlap.
 */
public final class Gfx {
    /** Global opacity multiplier, used for screen fade-ins. */
    public static float alpha = 1f;

    // Quads queued since the last flush: screen-space x/y pairs and a colour per vertex.
    private static float[] pendingXY = new float[512];
    private static int[] pendingColors = new int[256];
    private static int pending;
    private static float minX, minY, maxX, maxY;
    @Nullable
    private static GuiGraphicsExtractor pendingGraphics;
    @Nullable
    private static ScreenRectangle pendingScissor;
    private static final Vector2f TMP = new Vector2f();

    private Gfx() {
    }

    public static int fade(int argb) {
        if (alpha >= 1f) return argb;
        int a = (argb >>> 24) & 0xFF;
        return ((int) (a * Mth.clamp(alpha, 0f, 1f)) << 24) | (argb & 0xFFFFFF);
    }

    public static int withAlpha(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static void quad(GuiGraphicsExtractor g, float x1, float y1, int c1, float x2, float y2, int c2,
                            float x3, float y3, int c3, float x4, float y4, int c4) {
        ScreenRectangle scissor = g.peekScissorStack();
        // A batch is drawn with one scissor: start a new one when it changes.
        if (pending > 0 && (g != pendingGraphics || !Objects.equals(scissor, pendingScissor))) flush(pendingGraphics);
        pendingGraphics = g;
        pendingScissor = scissor;
        Matrix3x2f m = g.pose();
        vertex(m, x1, y1, fade(c1));
        vertex(m, x2, y2, fade(c2));
        vertex(m, x3, y3, fade(c3));
        vertex(m, x4, y4, fade(c4));
    }

    private static void vertex(Matrix3x2f m, float x, float y, int color) {
        if (pending == pendingColors.length) {
            pendingColors = Arrays.copyOf(pendingColors, pending * 2);
            pendingXY = Arrays.copyOf(pendingXY, pending * 4);
        }
        m.transformPosition(x, y, TMP);
        if (pending == 0) {
            minX = maxX = TMP.x;
            minY = maxY = TMP.y;
        } else {
            minX = Math.min(minX, TMP.x);
            maxX = Math.max(maxX, TMP.x);
            minY = Math.min(minY, TMP.y);
            maxY = Math.max(maxY, TMP.y);
        }
        pendingXY[pending * 2] = TMP.x;
        pendingXY[pending * 2 + 1] = TMP.y;
        pendingColors[pending] = color;
        pending++;
    }

    public static void tri(GuiGraphicsExtractor g, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        quad(g, x1, y1, c1, x2, y2, c2, x3, y3, c3, x3, y3, c3);
    }

    /** Submits the queued quads as one element, so later text/items draw on top in submission order. */
    public static void flush(GuiGraphicsExtractor g) {
        if (pending == 0 || pendingGraphics == null) {
            pending = 0;
            return;
        }
        int x0 = Mth.floor(minX), y0 = Mth.floor(minY);
        ScreenRectangle box = new ScreenRectangle(x0, y0, Math.max(1, Mth.ceil(maxX) - x0), Math.max(1, Mth.ceil(maxY) - y0));
        ScreenRectangle bounds = pendingScissor == null ? box : pendingScissor.intersection(box);
        if (bounds != null) {
            pendingGraphics.submitGuiElementRenderState(new Shape(Arrays.copyOf(pendingXY, pending * 2),
                    Arrays.copyOf(pendingColors, pending), pendingScissor, bounds));
        }
        pending = 0;
        pendingGraphics = null;
        pendingScissor = null;
    }

    public static void rect(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        quad(g, x0, y0, color, x0, y1, color, x1, y1, color, x1, y0, color);
        flush(g);
    }

    public static void vgrad(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int top, int bottom) {
        quad(g, x0, y0, top, x0, y1, bottom, x1, y1, bottom, x1, y0, top);
        flush(g);
    }

    public static void hgrad(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int left, int right) {
        quad(g, x0, y0, left, x0, y1, left, x1, y1, right, x1, y0, right);
        flush(g);
    }

    /** Convex polygon (counter-clockwise or clockwise), vertical gradient between y0..y1. */
    public static void poly(GuiGraphicsExtractor g, float[] xy, float y0, float y1, int top, int bottom) {
        int n = xy.length / 2;
        float cx = 0, cy = 0;
        for (int i = 0; i < n; i++) {
            cx += xy[i * 2];
            cy += xy[i * 2 + 1];
        }
        cx /= n;
        cy /= n;
        int cc = grad(cy, y0, y1, top, bottom);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ax = xy[i * 2], ay = xy[i * 2 + 1], bx = xy[j * 2], by = xy[j * 2 + 1];
            tri(g, cx, cy, cc, bx, by, grad(by, y0, y1, top, bottom), ax, ay, grad(ay, y0, y1, top, bottom));
        }
        flush(g);
    }

    private static int grad(float y, float y0, float y1, int top, int bottom) {
        return y1 <= y0 ? top : lerpColor(top, bottom, (y - y0) / (y1 - y0));
    }

    /** Points of a rectangle with chamfered corners (cuts: top-left, top-right, bottom-right, bottom-left). */
    public static float[] chamferPoints(float x, float y, float w, float h, float tl, float tr, float br, float bl) {
        return new float[]{
                x + tl, y, x + w - tr, y, x + w, y + tr, x + w, y + h - br,
                x + w - br, y + h, x + bl, y + h, x, y + h - bl, x, y + tl};
    }

    public static void chamfer(GuiGraphicsExtractor g, float x, float y, float w, float h, float tl, float tr, float br, float bl,
                               int top, int bottom) {
        poly(g, chamferPoints(x, y, w, h, tl, tr, br, bl), y, y + h, top, bottom);
    }

    /** Outline of a closed polygon. */
    public static void outline(GuiGraphicsExtractor g, float[] xy, float width, int color) {
        int n = xy.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            lineNoFlush(g, xy[i * 2], xy[i * 2 + 1], xy[j * 2], xy[j * 2 + 1], width, color);
        }
        flush(g);
    }

    public static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float width, int color) {
        lineNoFlush(g, x0, y0, x1, y1, width, color);
        flush(g);
    }

    private static void lineNoFlush(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float width, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        float nx = -dy / len * width / 2, ny = dx / len * width / 2;
        // Extend slightly so joints between segments are closed.
        float ex = dx / len * width / 2, ey = dy / len * width / 2;
        x0 -= ex;
        y0 -= ey;
        x1 += ex;
        y1 += ey;
        quad(g, x0 + nx, y0 + ny, color, x1 + nx, y1 + ny, color, x1 - nx, y1 - ny, color, x0 - nx, y0 - ny, color);
    }

    /** A filled pie slice clockwise from 12 o'clock, {@code frac} of a full turn. */
    public static void pie(GuiGraphicsExtractor g, float cx, float cy, float r, float frac, int color) {
        if (frac <= 0) return;
        int steps = Math.max(3, (int) (48 * frac));
        float a0 = -Mth.HALF_PI;
        for (int i = 0; i < steps; i++) {
            float a = a0 + Mth.TWO_PI * frac * i / steps;
            float b = a0 + Mth.TWO_PI * frac * (i + 1) / steps;
            tri(g, cx, cy, color, cx + Mth.cos(b) * r, cy + Mth.sin(b) * r, color, cx + Mth.cos(a) * r, cy + Mth.sin(a) * r, color);
        }
        flush(g);
    }

    /** A ring band between radii r0 and r1, angles in radians (0 = east, clockwise on screen). */
    public static void ring(GuiGraphicsExtractor g, float cx, float cy, float r0, float r1, float a0, float a1, int inner, int outer) {
        int steps = Math.max(6, (int) (Math.abs(a1 - a0) / Mth.TWO_PI * 64));
        for (int i = 0; i < steps; i++) {
            float a = a0 + (a1 - a0) * i / steps;
            float b = a0 + (a1 - a0) * (i + 1) / steps;
            quad(g, cx + Mth.cos(a) * r0, cy + Mth.sin(a) * r0, inner, cx + Mth.cos(a) * r1, cy + Mth.sin(a) * r1, outer,
                    cx + Mth.cos(b) * r1, cy + Mth.sin(b) * r1, outer, cx + Mth.cos(b) * r0, cy + Mth.sin(b) * r0, inner);
        }
        flush(g);
    }

    /** Regular polygon (hexagon for level badges, diamonds for markers). */
    public static float[] ngon(float cx, float cy, float r, int sides, float rotation) {
        float[] pts = new float[sides * 2];
        for (int i = 0; i < sides; i++) {
            float a = rotation + Mth.TWO_PI * i / sides;
            pts[i * 2] = cx + Mth.cos(a) * r;
            pts[i * 2 + 1] = cy + Mth.sin(a) * r;
        }
        return pts;
    }

    // ---- Composite elements --------------------------------------------------------

    /** The standard Frontier window panel: drop shadow, gradient body, border and accent corners. */
    public static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, int cut) {
        panel(g, x, y, w, h, cut, Theme.PANEL_TOP, Theme.PANEL_BOTTOM, Theme.BORDER, Theme.ACCENT);
    }

    public static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, int cut, int top, int bottom, int border, int accent) {
        float small = Math.max(1, cut / 3f);
        chamfer(g, x + 2, y + 3, w, h, cut, small, cut, small, 0x50000000, 0x70000000);
        chamfer(g, x, y, w, h, cut, small, cut, small, top, bottom);
        // Faint horizontal scanlines for the holographic look.
        for (float sy = y + 3; sy < y + h - 2; sy += 3) {
            quad(g, x + 2, sy, 0x07FFFFFF, x + 2, sy + 1, 0x07FFFFFF, x + w - 2, sy + 1, 0x07FFFFFF, x + w - 2, sy, 0x07FFFFFF);
        }
        flush(g);
        float[] pts = chamferPoints(x, y, w, h, cut, small, cut, small);
        outline(g, pts, 1f, border);
        // Accent brackets over the two big chamfers.
        float a = Math.min(18, w / 4);
        line(g, x, y + cut + 6, x, y + cut, 1.5f, accent);
        line(g, x, y + cut, x + cut, y, 1.5f, accent);
        line(g, x + cut, y, x + cut + a, y, 1.5f, accent);
        line(g, x + w, y + h - cut - 6, x + w, y + h - cut, 1.5f, accent);
        line(g, x + w, y + h - cut, x + w - cut, y + h, 1.5f, accent);
        line(g, x + w - cut, y + h, x + w - cut - a, y + h, 1.5f, accent);
    }

    /** A flat inset box used for sub-sections. */
    public static void inset(GuiGraphicsExtractor g, float x, float y, float w, float h) {
        chamfer(g, x, y, w, h, 3, 0, 3, 0, Theme.INSET_TOP, Theme.INSET_BOTTOM);
        outline(g, chamferPoints(x, y, w, h, 3, 0, 3, 0), 1f, Theme.INSET_BORDER);
    }

    /** A section header: small caps label with an accent rule. */
    public static void header(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int w) {
        flush(g);
        g.text(font, text, x, y, fade(Theme.ACCENT), false);
        int tw = font.width(text) + 4;
        hgrad(g, x + tw, y + 4, x + w, y + 5, Theme.withA(Theme.ACCENT, 0.7f), Theme.withA(Theme.ACCENT, 0f));
    }

    /** A progress bar with a gradient fill, a highlight line and optional segment ticks. */
    public static void bar(GuiGraphicsExtractor g, float x, float y, float w, float h, float frac, int c0, int c1, int back, int segments) {
        frac = Mth.clamp(frac, 0f, 1f);
        chamfer(g, x, y, w, h, 0, 0, Math.min(h, 3), 0, back, back);
        if (frac > 0) {
            float fw = w * frac;
            chamfer(g, x, y, fw, h, 0, 0, Math.min(h, 3) * Math.min(1f, fw / 3f), 0, c1, c0);
            hgrad(g, x, y, x + fw, y + Math.max(1, h / 3f), 0x40FFFFFF, 0x10FFFFFF);
        }
        if (segments > 1) {
            for (int i = 1; i < segments; i++) {
                float sx = x + w * i / segments;
                rect(g, sx, y, sx + 0.5f, y + h, 0x60000000);
            }
        }
    }

    public static void icon(GuiGraphicsExtractor g, Identifier tex, int x, int y, int size, int texSize) {
        flush(g);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, size, size, texSize, texSize, texSize, texSize,
                ARGB.white(Mth.clamp(alpha, 0f, 1f)));
    }

    /**
     * A live, mouse-following model of an entity in a box. The preview is rendered off-screen and placed
     * without the pose, so the box is moved to where the current pose (a sliding window) puts it.
     */
    public static void entity(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int size, float offsetY, float mouseX, float mouseY,
                              LivingEntity entity) {
        flush(g);
        Vector2f a = g.pose().transformPosition(x0, y0, new Vector2f());
        Vector2f b = g.pose().transformPosition(x1, y1, new Vector2f());
        InventoryScreen.extractEntityInInventoryFollowsMouse(g, Math.round(a.x), Math.round(a.y), Math.round(b.x), Math.round(b.y), size,
                offsetY, mouseX, mouseY, entity);
    }

    public static void text(GuiGraphicsExtractor g, Font font, Component text, float x, float y, float scale, int color, boolean shadow) {
        flush(g);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, text, 0, 0, fade(color), shadow);
        g.pose().popMatrix();
    }

    public static int wrapped(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int color, int lineHeight) {
        flush(g);
        List<FormattedCharSequence> lines = font.split(text, width);
        for (FormattedCharSequence line : lines) {
            g.text(font, line, x, y, fade(color), false);
            y += lineHeight;
        }
        return y;
    }

    /** A batch of coloured quads in screen space (the pose is already applied), drawn as one GUI element. */
    private record Shape(float[] xy, int[] colors, @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int i = 0; i < colors.length; i++) {
                consumer.addVertex(xy[i * 2], xy[i * 2 + 1], 0f).setColor(colors[i]);
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }
    }
}
