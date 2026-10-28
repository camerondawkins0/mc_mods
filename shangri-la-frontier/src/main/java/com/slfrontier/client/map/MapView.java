package com.slfrontier.client.map;

import com.mojang.math.Axis;
import com.slfrontier.client.ui.Gfx;
import com.slfrontier.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A view onto the explored map: centre (world x/z), zoom (screen pixels per block) and an
 * optional rotation. Draws tiles inside a screen rectangle and converts coordinates.
 */
public final class MapView {
    public double centerX, centerZ;
    public float zoom = 1f;
    /** Radians; the minimap can rotate with the camera. */
    public float rotation;
    public int x0, y0, x1, y1;

    public void setRect(int x0, int y0, int x1, int y1) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
    }

    public float screenCX() {
        return (x0 + x1) / 2f;
    }

    public float screenCY() {
        return (y0 + y1) / 2f;
    }

    /** Screen position of a world point. */
    public float[] toScreen(double wx, double wz) {
        double dx = (wx - centerX) * zoom, dz = (wz - centerZ) * zoom;
        if (rotation != 0) {
            double c = Math.cos(rotation), s = Math.sin(rotation);
            double rx = dx * c - dz * s, rz = dx * s + dz * c;
            dx = rx;
            dz = rz;
        }
        return new float[]{(float) (screenCX() + dx), (float) (screenCY() + dz)};
    }

    /** World position under a screen point. */
    public double[] toWorld(double sx, double sy) {
        double dx = sx - screenCX(), dz = sy - screenCY();
        if (rotation != 0) {
            double c = Math.cos(-rotation), s = Math.sin(-rotation);
            double rx = dx * c - dz * s, rz = dx * s + dz * c;
            dx = rx;
            dz = rz;
        }
        return new double[]{centerX + dx / zoom, centerZ + dz / zoom};
    }

    public boolean contains(float sx, float sy) {
        return sx >= x0 && sx < x1 && sy >= y0 && sy < y1;
    }

    /** Background, explored tiles and a faint coordinate grid. */
    public void draw(GuiGraphics g) {
        Gfx.vgrad(g, x0, y0, x1, y1, 0xF0081220, 0xF0050A14);
        g.enableScissor(x0, y0, x1, y1);
        // Unexplored hatching
        float span = (x1 - x0) + (y1 - y0);
        for (float d = -span; d < span; d += 6) {
            Gfx.line(g, x0 + d, y0, x0 + d + (y1 - y0), y1, 1f, 0x0C5FE0FF);
        }
        g.pose().pushPose();
        g.pose().translate(screenCX(), screenCY(), 0);
        if (rotation != 0) g.pose().mulPose(Axis.ZP.rotation(rotation));
        g.pose().scale(zoom, zoom, 1f);
        double halfDiag = Math.hypot(x1 - x0, y1 - y0) / 2 / zoom + MapStore.TILE;
        int tx0 = Mth.floor((centerX - halfDiag) / MapStore.TILE), tx1 = Mth.floor((centerX + halfDiag) / MapStore.TILE);
        int tz0 = Mth.floor((centerZ - halfDiag) / MapStore.TILE), tz1 = Mth.floor((centerZ + halfDiag) / MapStore.TILE);
        // Integer origin so tile blits land on whole blocks; the fractional part is a translation.
        int ox = Mth.floor(centerX), oz = Mth.floor(centerZ);
        g.pose().translate(-(float) (centerX - ox), -(float) (centerZ - oz), 0);
        for (int tx = tx0; tx <= tx1; tx++) {
            for (int tz = tz0; tz <= tz1; tz++) {
                ResourceLocation tex = MapTextures.get(tx, tz);
                if (tex == null) continue;
                int bx = tx * MapStore.TILE - ox, bz = tz * MapStore.TILE - oz;
                g.setColor(1f, 1f, 1f, Mth.clamp(Gfx.alpha, 0f, 1f));
                g.blit(tex, bx, bz, 0, 0, MapStore.TILE, MapStore.TILE, MapStore.TILE, MapStore.TILE);
                g.setColor(1f, 1f, 1f, 1f);
            }
        }
        g.pose().popPose();
        // Grid every 256 blocks when zoomed out enough to need orientation.
        if (rotation == 0 && zoom <= 2f) {
            int step = zoom < 0.5f ? 512 : 256;
            double[] tl = toWorld(x0, y0), br = toWorld(x1, y1);
            for (long gx = Mth.floor(tl[0] / step) * (long) step; gx <= br[0]; gx += step) {
                float sx = toScreen(gx, 0)[0];
                Gfx.rect(g, sx, y0, sx + 0.5f, y1, 0x225FE0FF);
            }
            for (long gz = Mth.floor(tl[1] / step) * (long) step; gz <= br[1]; gz += step) {
                float sy = toScreen(0, gz)[1];
                Gfx.rect(g, x0, sy, x1, sy + 0.5f, 0x225FE0FF);
            }
        }
        g.disableScissor();
    }

    /** Draws a square icon centred on a screen point, optionally rotated (radians). */
    public static void icon(GuiGraphics g, ResourceLocation tex, float sx, float sy, int size, float rot) {
        g.pose().pushPose();
        g.pose().translate(sx, sy, 0);
        if (rot != 0) g.pose().mulPose(Axis.ZP.rotation(rot));
        g.setColor(1f, 1f, 1f, Mth.clamp(Gfx.alpha, 0f, 1f));
        g.blit(tex, -size / 2, -size / 2, size, size, 0f, 0f, 16, 16, 16, 16);
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    /** Clamps a screen point into the view (for off-screen objective arrows); returns {x, y, clamped?1:0}. */
    public float[] clampToEdge(float sx, float sy, float margin) {
        float cx = screenCX(), cy = screenCY();
        float hx = (x1 - x0) / 2f - margin, hy = (y1 - y0) / 2f - margin;
        float dx = sx - cx, dy = sy - cy;
        if (Math.abs(dx) <= hx && Math.abs(dy) <= hy) return new float[]{sx, sy, 0};
        float t = Math.min(hx / Math.max(1e-3f, Math.abs(dx)), hy / Math.max(1e-3f, Math.abs(dy)));
        return new float[]{cx + dx * t, cy + dy * t, 1};
    }

    public static int markerColor(int kind) {
        return switch (kind) {
            case 0 -> Theme.GOLD;
            case 1 -> Theme.ACCENT;
            case 3 -> 0xFFF2C27A;
            default -> 0xFF7FE7FF;
        };
    }
}
