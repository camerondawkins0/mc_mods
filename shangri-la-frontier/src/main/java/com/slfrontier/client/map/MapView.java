package com.slfrontier.client.map;

import com.slfrontier.client.ui.Gfx;
import com.slfrontier.client.ui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * A view onto the explored map: centre (world x/z), zoom (screen pixels per block) and an
 * optional rotation. Draws tiles inside a screen rectangle and converts coordinates. Zoomed out
 * below {@link #OVERVIEW_ZOOM} it draws quarter-resolution overviews (one per 512 blocks), and with
 * {@link #underlay} on, the designed continent shows faintly beneath what has been explored.
 */
public final class MapView {
    public double centerX, centerZ;
    public float zoom = 1f;
    /** Radians; the minimap can rotate with the camera. */
    public float rotation;
    public int x0, y0, x1, y1;
    /** Draw the designed continent under the explored tiles (the map screen; the minimap stays plain). */
    public boolean underlay;
    /** Below this zoom, overview textures replace the full-resolution tiles. */
    public static final float OVERVIEW_ZOOM = 0.5f;

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

    /** Background, the designed continent (if shown), explored tiles and a faint coordinate grid. */
    public void draw(GuiGraphicsExtractor g) {
        MapTextures.beginFrame();
        Gfx.vgrad(g, x0, y0, x1, y1, 0xF0081220, 0xF0050A14);
        g.enableScissor(x0, y0, x1, y1);
        int tint = ARGB.white(Mth.clamp(Gfx.alpha, 0f, 1f));
        if (underlay && rotation == 0 && DesignMap.ready()) {
            float[] s = toScreen(DesignMap.x0(), DesignMap.z0());
            g.pose().pushMatrix();
            g.pose().translate(s[0], s[1]);
            g.pose().scale(zoom, zoom);
            g.blit(RenderPipelines.GUI_TEXTURED, DesignMap.texture(), 0, 0, 0, 0, DesignMap.width(), DesignMap.height(),
                    DesignMap.texWidth(), DesignMap.texHeight(), DesignMap.texWidth(), DesignMap.texHeight(), tint);
            g.pose().popMatrix();
        }
        // Unexplored hatching, as one batch.
        float span = (x1 - x0) + (y1 - y0);
        for (float d = -span; d < span; d += 6) {
            Gfx.lineQueued(g, x0 + d, y0, x0 + d + (y1 - y0), y1, 1f, 0x0C5FE0FF);
        }
        Gfx.flush(g);
        g.pose().pushMatrix();
        g.pose().translate(screenCX(), screenCY());
        if (rotation != 0) g.pose().rotate(rotation);
        g.pose().scale(zoom, zoom);
        double halfDiag = Math.hypot(x1 - x0, y1 - y0) / 2 / zoom + MapStore.TILE;
        int tx0 = Mth.floor((centerX - halfDiag) / MapStore.TILE), tx1 = Mth.floor((centerX + halfDiag) / MapStore.TILE);
        int tz0 = Mth.floor((centerZ - halfDiag) / MapStore.TILE), tz1 = Mth.floor((centerZ + halfDiag) / MapStore.TILE);
        // Integer origin so tile blits land on whole blocks; the fractional part is a translation.
        int ox = Mth.floor(centerX), oz = Mth.floor(centerZ);
        g.pose().translate(-(float) (centerX - ox), -(float) (centerZ - oz));
        if (zoom < OVERVIEW_ZOOM) {
            int n = MapTextures.OVERVIEW, span4 = MapStore.TILE * n;
            for (int rx = Math.floorDiv(tx0, n); rx <= Math.floorDiv(tx1, n); rx++) {
                for (int rz = Math.floorDiv(tz0, n); rz <= Math.floorDiv(tz1, n); rz++) {
                    Identifier tex = MapTextures.overview(rx, rz);
                    if (tex == null) continue;
                    g.blit(RenderPipelines.GUI_TEXTURED, tex, rx * span4 - ox, rz * span4 - oz, 0, 0, span4, span4,
                            MapStore.TILE, MapStore.TILE, MapStore.TILE, MapStore.TILE, tint);
                }
            }
        } else {
            for (int tx = tx0; tx <= tx1; tx++) {
                for (int tz = tz0; tz <= tz1; tz++) {
                    Identifier tex = MapTextures.get(tx, tz);
                    if (tex == null) continue;
                    int bx = tx * MapStore.TILE - ox, bz = tz * MapStore.TILE - oz;
                    g.blit(RenderPipelines.GUI_TEXTURED, tex, bx, bz, 0, 0, MapStore.TILE, MapStore.TILE, MapStore.TILE, MapStore.TILE, tint);
                }
            }
        }
        g.pose().popMatrix();
        // Grid every 256 blocks when zoomed out enough to need orientation.
        if (rotation == 0 && zoom <= 2f) {
            int step = zoom < 0.5f ? 512 : 256;
            double[] tl = toWorld(x0, y0), br = toWorld(x1, y1);
            for (long gx = Mth.floor(tl[0] / step) * (long) step; gx <= br[0]; gx += step) {
                float sx = toScreen(gx, 0)[0];
                Gfx.rectQueued(g, sx, y0, sx + 0.5f, y1, 0x225FE0FF);
            }
            for (long gz = Mth.floor(tl[1] / step) * (long) step; gz <= br[1]; gz += step) {
                float sy = toScreen(0, gz)[1];
                Gfx.rectQueued(g, x0, sy, x1, sy + 0.5f, 0x225FE0FF);
            }
            Gfx.flush(g);
        }
        g.disableScissor();
    }

    /** Region names over the designed continent, fading in as the view zooms out. */
    public void drawRegionLabels(GuiGraphicsExtractor g, Font font) {
        if (!underlay || rotation != 0 || zoom > 0.6f || !DesignMap.ready()) return;
        float fade = Mth.clamp((0.6f - zoom) / 0.25f, 0f, 1f) * Mth.clamp(Gfx.alpha, 0f, 1f);
        if (fade < 0.05f) return;
        g.enableScissor(x0, y0, x1, y1);
        float saved = Gfx.alpha;
        Gfx.alpha = fade;
        for (DesignMap.Label label : DesignMap.labels()) {
            float[] s = toScreen(label.x(), label.z());
            if (!contains(s[0], s[1])) continue;
            int w = font.width(label.name());
            Gfx.text(g, font, label.name(), s[0] - w / 2f, s[1] - 4, 1f, 0xFFF2E6B8, true);
        }
        Gfx.alpha = saved;
        g.disableScissor();
    }

    /** Draws a square icon centred on a screen point, optionally rotated (radians). */
    public static void icon(GuiGraphicsExtractor g, Identifier tex, float sx, float sy, int size, float rot) {
        Gfx.flush(g);
        g.pose().pushMatrix();
        g.pose().translate(sx, sy);
        if (rot != 0) g.pose().rotate(rot);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, -size / 2, -size / 2, 0f, 0f, size, size, 16, 16, 16, 16,
                ARGB.white(Mth.clamp(Gfx.alpha, 0f, 1f)));
        g.pose().popMatrix();
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
