package com.slfrontier.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.slfrontier.SLFrontier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.Inflater;

/**
 * The designed Frontier continent (worldmap/frontier.bin, written by tools/worldmap.py), drawn
 * faintly under the explored map: the land's shape, its regions and relief, so the map screen
 * isn't an empty void before you've been anywhere. Built once, off the render thread, the first
 * time the map screen asks for it.
 */
public final class DesignMap {
    private static final String FILE = "/data/slfrontier/worldmap/frontier.bin";
    private static final Identifier TEXTURE = SLFrontier.id("map/design");
    /** Water fills below this height (tools/worldmap.py SEA). */
    private static final int SEA = 63;
    /** Regions smaller than this many cells are set pieces (towns, lairs), named by their waypoints instead. */
    private static final int REGION_CELLS = 3000;
    /** How far the colours fade toward the map's navy background. */
    private static final float FADE = 0.62f;
    private static final int BACKGROUND = 0x081220;
    /** Biome colours of tools/worldmap.py's preview render; the Hidden Garden passes for forest (it should be found, not read off a map). */
    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("frontier_sea", 0x345CA0), Map.entry("frontier_shore", 0xDED0A0), Map.entry("fastera_plains", 0x84BA5C),
            Map.entry("rampant_forest", 0x3E8438), Map.entry("nightslayer_woods", 0x343E4A), Map.entry("diremarsh_waste", 0x687046),
            Map.entry("prismatic_forest_grotto", 0x7EA0D2), Map.entry("lightless_barrens", 0x4E4858),
            Map.entry("thirdrema_highlands", 0x949668), Map.entry("hidden_garden", 0x3E8438), Map.entry("frontier_peaks", 0xB4B4BA),
            Map.entry("goblin_camp", 0x966E46), Map.entry("serpent_bridge", 0xAAAAAA), Map.entry("rabbit_isle", 0xECB0C4));
    /** Towns show as towns; lairs and ruins blend in, to be discovered rather than spotted. */
    private static final int TOWN_COLOR = 0xEED678, SITE_COLOR = 0x6E7466;

    /** A region's name and where to write it (block coordinates). */
    public record Label(Component name, int x, int z) {
    }

    private record Built(int x0, int z0, int cell, int w, int h, int[] pixels, List<Label> labels) {
    }

    @Nullable
    private static CompletableFuture<Built> loading;
    @Nullable
    private static Built built;
    private static boolean uploaded, failed;

    private DesignMap() {
    }

    /** Starts building the underlay if it hasn't been; true once it can be drawn. */
    public static boolean ready() {
        if (uploaded) return true;
        if (failed) return false;
        if (loading == null) loading = CompletableFuture.supplyAsync(DesignMap::build, Util.backgroundExecutor());
        if (!loading.isDone()) return false;
        try {
            built = loading.join();
        } catch (RuntimeException e) {
            SLFrontier.LOGGER.warn("Could not build the Frontier map underlay", e);
            failed = true;
            return false;
        }
        NativeImage img = new NativeImage(built.w(), built.h(), false);
        for (int z = 0; z < built.h(); z++) {
            for (int x = 0; x < built.w(); x++) img.setPixel(x, z, built.pixels()[z * built.w() + x]);
        }
        Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(TEXTURE::toString, img));
        uploaded = true;
        return true;
    }

    public static Identifier texture() {
        return TEXTURE;
    }

    /** Block x of the map's west edge. Only valid once {@link #ready()}. */
    public static int x0() {
        return built.x0();
    }

    public static int z0() {
        return built.z0();
    }

    /** Width in blocks. */
    public static int width() {
        return built.w() * built.cell();
    }

    public static int height() {
        return built.h() * built.cell();
    }

    public static int texWidth() {
        return built.w();
    }

    public static int texHeight() {
        return built.h();
    }

    public static List<Label> labels() {
        return built == null ? List.of() : built.labels();
    }

    private static Built build() {
        try (InputStream raw = DesignMap.class.getResourceAsStream(FILE)) {
            if (raw == null) throw new IOException("Missing " + FILE);
            DataInputStream in = new DataInputStream(raw);
            if (!"SLFW".equals(new String(in.readNBytes(4), StandardCharsets.US_ASCII))) throw new IOException("Bad Frontier map file");
            in.readInt(); // version
            int x0 = in.readInt(), z0 = in.readInt(), cell = in.readInt(), w = in.readInt(), h = in.readInt();
            int biomes = in.readInt();
            List<String> names = new ArrayList<>(biomes);
            for (int i = 0; i < biomes; i++) names.add(in.readUTF());
            byte[] packed = in.readNBytes(in.readInt());
            int n = w * h;
            byte[] data = new byte[n * 5];
            Inflater inflater = new Inflater();
            try {
                inflater.setInput(packed);
                int read = 0;
                while (read < data.length && !inflater.finished()) read += inflater.inflate(data, read, data.length - read);
            } catch (java.util.zip.DataFormatException e) {
                throw new IOException("Corrupt Frontier map file", e);
            } finally {
                inflater.end();
            }
            float[] height = new float[n];
            for (int i = 0; i < n; i++) height[i] = (((data[2 * i] & 0xFF) << 8) | (data[2 * i + 1] & 0xFF)) / 16f;
            int[] palette = new int[biomes];
            for (int b = 0; b < biomes; b++) {
                String name = names.get(b);
                boolean town = name.equals("firstia") || name.equals("secondil") || name.equals("thirdrema") || name.equals("rabituza");
                palette[b] = COLORS.getOrDefault(name, town ? TOWN_COLOR : SITE_COLOR);
            }
            int[] pixels = new int[n];
            int[] count = new int[biomes];
            int[][] histX = new int[biomes][w], histZ = new int[biomes][h];
            for (int z = 0; z < h; z++) {
                for (int x = 0; x < w; x++) {
                    int i = z * w + x;
                    int b = data[4 * n + i] & 0xFF;
                    if (b >= biomes) b = 0;
                    count[b]++;
                    histX[b][x]++;
                    histZ[b][z]++;
                    float hgt = height[i];
                    int rgb;
                    if (hgt < SEA - 0.5f) {
                        float deep = Math.min(1f, (SEA - hgt) / 30f);
                        rgb = mix(0x3C6EBE, 0x18346E, deep);
                    } else {
                        // Relief: lit from the north-west, like the design preview.
                        float gx = (height[z * w + Math.min(w - 1, x + 1)] - height[z * w + Math.max(0, x - 1)]) / 2f;
                        float gz = (height[Math.min(h - 1, z + 1) * w + x] - height[Math.max(0, z - 1) * w + x]) / 2f;
                        float shade = Math.clamp(1 + (gx - gz) * 0.12f, 0.55f, 1.35f);
                        float alt = Math.clamp((hgt - 64) / 140f, 0f, 1f);
                        rgb = scale(palette[b], shade * (0.9f + 0.25f * alt));
                    }
                    pixels[i] = 0xFF000000 | mix(rgb, BACKGROUND, FADE);
                }
            }
            List<Label> labels = new ArrayList<>();
            for (int b = 0; b < biomes; b++) {
                String name = names.get(b);
                if (count[b] < REGION_CELLS || name.equals("frontier_sea") || name.equals("frontier_shore")
                        || name.equals("hidden_garden")) continue;
                int mx = median(histX[b], count[b]), mz = median(histZ[b], count[b]);
                labels.add(new Label(Component.translatable("biome.slfrontier." + name), x0 + mx * cell + cell / 2, z0 + mz * cell + cell / 2));
            }
            return new Built(x0, z0, cell, w, h, pixels, List.copyOf(labels));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static int median(int[] hist, int total) {
        int half = total / 2, sum = 0;
        for (int i = 0; i < hist.length; i++) {
            sum += hist[i];
            if (sum > half) return i;
        }
        return hist.length / 2;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    private static int scale(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * k));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * k));
        int b = Math.min(255, (int) ((rgb & 0xFF) * k));
        return r << 16 | g << 8 | b;
    }
}
