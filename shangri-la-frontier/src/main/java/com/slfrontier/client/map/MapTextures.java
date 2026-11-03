package com.slfrontier.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.slfrontier.SLFrontier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * GPU textures for the explored map: one per 128x128-block tile for close views, and a quarter-
 * resolution overview per 4x4 tiles for zoomed-out views (one draw per 512 blocks instead of 16).
 * Changed tiles re-upload at most twice a second, and textures not drawn for a while are released
 * once there are more than the caps allow.
 */
public final class MapTextures {
    /** Tiles per overview side; an overview pixel covers OVERVIEW x OVERVIEW blocks. */
    public static final int OVERVIEW = 4;
    private static final int MAX_TILES = 256, MAX_OVERVIEWS = 192;
    private static final long TILE_UPLOAD_MS = 500, OVERVIEW_UPLOAD_MS = 1000;

    private static final class Entry {
        final Identifier id;
        final DynamicTexture texture;
        long lastUsed;
        long uploadedAt;
        int version = Integer.MIN_VALUE;

        Entry(Identifier id, DynamicTexture texture) {
            this.id = id;
            this.texture = texture;
        }
    }

    private static final Map<Long, Entry> TILES = new HashMap<>();
    private static final Map<Long, Entry> OVERVIEWS = new HashMap<>();
    /** ARGB for every packed map colour, already tinted toward the UI's navy. */
    private static final int[] PALETTE = new int[256];
    private static long frame;

    static {
        for (int i = 1; i < 256; i++) PALETTE[i] = tint(MapColor.getColorFromPackedId(i));
    }

    private MapTextures() {
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** Marks the start of a map draw: textures used since are the ones on screen. */
    static void beginFrame() {
        frame++;
    }

    /** The texture for a tile, or null if nothing there has been explored. */
    @Nullable
    public static Identifier get(int tx, int tz) {
        MapStore.Tile tile = MapStore.tileIfExplored(tx, tz);
        if (tile == null) return null;
        long key = key(tx, tz);
        Entry e = TILES.get(key);
        if (e == null) {
            e = create("map/tile_", tx, tz, MapStore.TILE);
            TILES.put(key, e);
            evict(TILES, MAX_TILES);
        }
        e.lastUsed = frame;
        long now = Util.getMillis();
        if (e.version != tile.version && (e.version == Integer.MIN_VALUE || now - e.uploadedAt >= TILE_UPLOAD_MS)) {
            NativeImage img = e.texture.getPixels();
            byte[] colors = tile.colors;
            for (int z = 0, i = 0; z < MapStore.TILE; z++) {
                for (int x = 0; x < MapStore.TILE; x++, i++) img.setPixel(x, z, PALETTE[colors[i] & 0xFF]);
            }
            e.texture.upload();
            e.version = tile.version;
            e.uploadedAt = now;
            tile.textureDirty = false;
        }
        return e.id;
    }

    /**
     * The overview texture for the 4x4 tiles starting at tile (ox * 4, oz * 4): one pixel per 4x4 blocks,
     * or null if none of them is explored.
     */
    @Nullable
    public static Identifier overview(int ox, int oz) {
        int version = 1;
        boolean any = false;
        for (int i = 0; i < OVERVIEW * OVERVIEW; i++) {
            MapStore.Tile t = MapStore.tileIfExplored(ox * OVERVIEW + i % OVERVIEW, oz * OVERVIEW + i / OVERVIEW);
            version = version * 31 + (t == null ? 0 : t.version + 1);
            any |= t != null;
        }
        if (!any) return null;
        long key = key(ox, oz);
        Entry e = OVERVIEWS.get(key);
        if (e == null) {
            e = create("map/overview_", ox, oz, MapStore.TILE);
            OVERVIEWS.put(key, e);
            evict(OVERVIEWS, MAX_OVERVIEWS);
        }
        e.lastUsed = frame;
        long now = Util.getMillis();
        if (e.version != version && (e.version == Integer.MIN_VALUE || now - e.uploadedAt >= OVERVIEW_UPLOAD_MS)) {
            NativeImage img = e.texture.getPixels();
            int sub = MapStore.TILE / OVERVIEW;
            for (int i = 0; i < OVERVIEW * OVERVIEW; i++) {
                MapStore.Tile t = MapStore.tileIfExplored(ox * OVERVIEW + i % OVERVIEW, oz * OVERVIEW + i / OVERVIEW);
                int px0 = (i % OVERVIEW) * sub, pz0 = (i / OVERVIEW) * sub;
                for (int z = 0; z < sub; z++) {
                    for (int x = 0; x < sub; x++) {
                        int c = 0;
                        if (t != null) {
                            // The middle of the 4x4 blocks, or any explored corner of them at the edge of the known.
                            int bx = x * OVERVIEW, bz = z * OVERVIEW;
                            c = t.colors[(bz + 2) * MapStore.TILE + bx + 2] & 0xFF;
                            if (c == 0) c = t.colors[bz * MapStore.TILE + bx] & 0xFF;
                            if (c == 0) c = t.colors[(bz + 3) * MapStore.TILE + bx + 3] & 0xFF;
                        }
                        img.setPixel(px0 + x, pz0 + z, PALETTE[c]);
                    }
                }
            }
            e.texture.upload();
            e.version = version;
            e.uploadedAt = now;
        }
        return e.id;
    }

    private static Entry create(String prefix, int x, int z, int size) {
        Identifier id = SLFrontier.id(prefix + (x < 0 ? "n" + -x : x) + "_" + (z < 0 ? "n" + -z : z));
        DynamicTexture tex = new DynamicTexture(id::toString, size, size, true);
        Minecraft.getInstance().getTextureManager().register(id, tex);
        Entry e = new Entry(id, tex);
        e.lastUsed = frame;
        return e;
    }

    /** Releases the textures drawn longest ago while there are more than {@code max} (never one drawn this frame). */
    private static void evict(Map<Long, Entry> map, int max) {
        var tm = Minecraft.getInstance().getTextureManager();
        while (map.size() > max) {
            Long oldest = null;
            long oldestUse = Long.MAX_VALUE;
            for (Map.Entry<Long, Entry> e : map.entrySet()) {
                if (e.getValue().lastUsed < oldestUse) {
                    oldestUse = e.getValue().lastUsed;
                    oldest = e.getKey();
                }
            }
            if (oldest == null || oldestUse >= frame) return;
            tm.release(map.remove(oldest).id);
        }
    }

    /** Nudges map colours toward the navy UI palette (ARGB in, ARGB out). */
    private static int tint(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r = (int) (r * 0.86 + 0x14 * 0.14);
        g = (int) (g * 0.86 + 0x23 * 0.14);
        b = (int) (b * 0.86 + 0x38 * 0.14);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    public static void releaseAll() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (Iterator<Entry> it = TILES.values().iterator(); it.hasNext(); ) tm.release(it.next().id);
        for (Iterator<Entry> it = OVERVIEWS.values().iterator(); it.hasNext(); ) tm.release(it.next().id);
        TILES.clear();
        OVERVIEWS.clear();
    }
}
