package com.slfrontier.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.slfrontier.SLFrontier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** GPU textures for explored map tiles, created on demand and refreshed when a tile changes. */
public final class MapTextures {
    private record Entry(ResourceLocation id, DynamicTexture texture) {
    }

    private static final Map<Long, Entry> TEXTURES = new HashMap<>();

    private MapTextures() {
    }

    /** The texture for a tile, or null if nothing there has been explored. */
    @Nullable
    public static ResourceLocation get(int tx, int tz) {
        MapStore.Tile tile = MapStore.tileIfExplored(tx, tz);
        if (tile == null) return null;
        long key = ((long) tx << 32) ^ (tz & 0xFFFFFFFFL);
        Entry e = TEXTURES.get(key);
        if (e == null) {
            DynamicTexture tex = new DynamicTexture(MapStore.TILE, MapStore.TILE, false);
            ResourceLocation id = SLFrontier.id("map/tile_" + (tx < 0 ? "n" + -tx : tx) + "_" + (tz < 0 ? "n" + -tz : tz));
            Minecraft.getInstance().getTextureManager().register(id, tex);
            e = new Entry(id, tex);
            TEXTURES.put(key, e);
            tile.textureDirty = true;
        }
        if (tile.textureDirty) {
            fill(tile, e.texture().getPixels());
            e.texture().upload();
            tile.textureDirty = false;
        }
        return e.id();
    }

    private static void fill(MapStore.Tile tile, NativeImage img) {
        for (int z = 0; z < MapStore.TILE; z++) {
            for (int x = 0; x < MapStore.TILE; x++) {
                int packed = tile.colors[z * MapStore.TILE + x] & 0xFF;
                img.setPixelRGBA(x, z, packed == 0 ? 0 : tint(MapColor.getColorFromPackedId(packed)));
            }
        }
    }

    /** Nudges map colours toward the navy UI palette (ABGR in, ABGR out). */
    private static int tint(int abgr) {
        int r = abgr & 0xFF, g = (abgr >> 8) & 0xFF, b = (abgr >> 16) & 0xFF;
        r = (int) (r * 0.86 + 0x14 * 0.14);
        g = (int) (g * 0.86 + 0x23 * 0.14);
        b = (int) (b * 0.86 + 0x38 * 0.14);
        return 0xFF000000 | b << 16 | g << 8 | r;
    }

    public static void releaseAll() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (Entry e : TEXTURES.values()) tm.release(e.id());
        TEXTURES.clear();
    }
}
