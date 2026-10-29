package com.slfrontier.world.design;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slfrontier.SLFrontier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * The hand-designed Frontier continent, made by tools/worldmap.py: terrain height, roughness and
 * crag grids, the biome map, the plots every town and set piece stands on, and the roads.
 * Loaded once from the mod's resources; everything here is read-only and thread-safe.
 */
public final class FrontierDesign {
    private static final String ROOT = "/data/slfrontier/worldmap/";
    private static final int ROAD_CELL = 32;

    /** A template placed at a fixed spot; {@code top} is the y of the ground block around it. */
    public record Site(String id, ResourceLocation template, BlockPos origin, int sizeX, int sizeZ, int top, boolean town) {
        public BlockPos center() {
            return new BlockPos(origin.getX() + sizeX / 2, top + 1, origin.getZ() + sizeZ / 2);
        }

        /** The chunk the structure starts in (its centre); vanilla limits pieces to 8 chunks from it. */
        public ChunkPos startChunk() {
            return new ChunkPos(center());
        }

        public boolean contains(int x, int z, int margin) {
            return x >= origin.getX() - margin && x < origin.getX() + sizeX + margin
                    && z >= origin.getZ() - margin && z < origin.getZ() + sizeZ + margin;
        }
    }

    /** One straight piece of road between two centre-line points. */
    public record Segment(float ax, float az, float bx, float bz, float halfWidth, boolean main) {
        public double distance(double x, double z) {
            double dx = bx - ax, dz = bz - az;
            double len = dx * dx + dz * dz;
            double t = len < 1e-6 ? 0 : Mth.clamp(((x - ax) * dx + (z - az) * dz) / len, 0, 1);
            return Math.hypot(x - (ax + t * dx), z - (az + t * dz));
        }
    }

    /** A lantern post beside a main road. */
    public record Lamp(int x, int z) {
    }

    private static volatile FrontierDesign instance;

    private final int x0, z0, cell, w, h;
    private final short[] height;
    private final byte[] rough, crag, biome;
    private final List<String> biomeNames;
    private final Map<String, Site> sites = new LinkedHashMap<>();
    private final Map<Long, List<Segment>> roadIndex = new java.util.HashMap<>();
    private final Map<Long, List<Lamp>> lampIndex = new java.util.HashMap<>();

    public static FrontierDesign get() {
        FrontierDesign d = instance;
        if (d == null) {
            synchronized (FrontierDesign.class) {
                d = instance;
                if (d == null) {
                    try {
                        d = new FrontierDesign();
                    } catch (IOException | DataFormatException e) {
                        throw new IllegalStateException("Could not load the Frontier map", e);
                    }
                    instance = d;
                }
            }
        }
        return d;
    }

    private FrontierDesign() throws IOException, DataFormatException {
        try (DataInputStream in = new DataInputStream(open("frontier.bin"))) {
            byte[] magic = in.readNBytes(4);
            if (!"SLFW".equals(new String(magic, StandardCharsets.US_ASCII))) throw new IOException("Bad Frontier map file");
            in.readInt();  // version
            x0 = in.readInt();
            z0 = in.readInt();
            cell = in.readInt();
            w = in.readInt();
            h = in.readInt();
            int biomes = in.readInt();
            biomeNames = new ArrayList<>(biomes);
            for (int i = 0; i < biomes; i++) biomeNames.add(in.readUTF());
            byte[] packed = in.readNBytes(in.readInt());
            int n = w * h;
            byte[] raw = new byte[n * 5];
            Inflater inflater = new Inflater();
            inflater.setInput(packed);
            int read = 0;
            while (read < raw.length && !inflater.finished()) read += inflater.inflate(raw, read, raw.length - read);
            inflater.end();
            height = new short[n];
            for (int i = 0; i < n; i++) height[i] = (short) (((raw[2 * i] & 0xFF) << 8) | (raw[2 * i + 1] & 0xFF));
            rough = java.util.Arrays.copyOfRange(raw, 2 * n, 3 * n);
            crag = java.util.Arrays.copyOfRange(raw, 3 * n, 4 * n);
            biome = java.util.Arrays.copyOfRange(raw, 4 * n, 5 * n);
        }
        for (JsonElement e : readJson("sites.json").getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            Site site = new Site(o.get("id").getAsString(), ResourceLocation.parse(o.get("template").getAsString()),
                    new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()),
                    o.get("size_x").getAsInt(), o.get("size_z").getAsInt(), o.get("top").getAsInt(), o.get("town").getAsBoolean());
            sites.put(site.id(), site);
        }
        for (JsonElement e : readJson("roads.json").getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            boolean main = "main".equals(o.get("style").getAsString());
            float half = o.get("width").getAsFloat() / 2f;
            JsonArray pts = o.getAsJsonArray("points");
            float travelled = 0;
            for (int i = 0; i + 1 < pts.size(); i++) {
                JsonArray a = pts.get(i).getAsJsonArray(), b = pts.get(i + 1).getAsJsonArray();
                Segment s = new Segment(a.get(0).getAsFloat(), a.get(1).getAsFloat(), b.get(0).getAsFloat(), b.get(1).getAsFloat(),
                        half, main);
                index(s);
                float len = (float) Math.hypot(s.bx() - s.ax(), s.bz() - s.az());
                // A lantern every 48 blocks along the main roads, alternating sides.
                if (main && (int) (travelled / 48) != (int) ((travelled + len) / 48) && len > 0) {
                    float side = ((int) (travelled / 48) % 2 == 0) ? 1 : -1;
                    float nx = -(s.bz() - s.az()) / len * side, nz = (s.bx() - s.ax()) / len * side;
                    Lamp lamp = new Lamp(Mth.floor(s.bx() + nx * (half + 1.5f)), Mth.floor(s.bz() + nz * (half + 1.5f)));
                    lampIndex.computeIfAbsent(ChunkPos.asLong(lamp.x() >> 4, lamp.z() >> 4), k -> new ArrayList<>()).add(lamp);
                }
                travelled += len;
            }
        }
        SLFrontier.LOGGER.info("Loaded the Frontier map: {}x{} blocks, {} sites", w * cell, h * cell, sites.size());
    }

    private void index(Segment s) {
        int pad = (int) Math.ceil(s.halfWidth()) + 2;
        int cx0 = Math.floorDiv((int) Math.min(s.ax(), s.bx()) - pad, ROAD_CELL);
        int cx1 = Math.floorDiv((int) Math.max(s.ax(), s.bx()) + pad, ROAD_CELL);
        int cz0 = Math.floorDiv((int) Math.min(s.az(), s.bz()) - pad, ROAD_CELL);
        int cz1 = Math.floorDiv((int) Math.max(s.az(), s.bz()) + pad, ROAD_CELL);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                roadIndex.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new ArrayList<>()).add(s);
            }
        }
    }

    private static InputStream open(String name) throws IOException {
        InputStream in = FrontierDesign.class.getResourceAsStream(ROOT + name);
        if (in == null) throw new IOException("Missing " + ROOT + name);
        return in;
    }

    private static JsonElement readJson(String name) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(open(name), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    // ---- Terrain -----------------------------------------------------------------------

    /** Ground height (the surface sits just below this value), blended between grid points. */
    public double height(int x, int z) {
        return bilinear(x, z, 0) / 16.0;
    }

    /** How many blocks the fine detail noise may raise or lower the ground. */
    public double roughness(int x, int z) {
        return bilinear(x, z, 1) / 8.0;
    }

    /** 0..1: how much 3D noise may carve cliffs and overhangs. */
    public double crag(int x, int z) {
        return bilinear(x, z, 2) / 255.0;
    }

    private double bilinear(int x, int z, int channel) {
        double fx = (x - x0) / (double) cell, fz = (z - z0) / (double) cell;
        int ix = Mth.floor(fx), iz = Mth.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        return Mth.lerp2(tx, tz, raw(ix, iz, channel), raw(ix + 1, iz, channel), raw(ix, iz + 1, channel), raw(ix + 1, iz + 1, channel));
    }

    private double raw(int ix, int iz, int channel) {
        if (ix < 0 || iz < 0 || ix >= w || iz >= h) {
            return switch (channel) {
                case 0 -> 34 * 16;  // open sea beyond the map
                case 1 -> 12;
                default -> 0;
            };
        }
        int i = iz * w + ix;
        return switch (channel) {
            case 0 -> height[i];
            case 1 -> rough[i] & 0xFF;
            default -> crag[i] & 0xFF;
        };
    }

    /** The designed biome's name (without namespace) at a block. */
    public String biomeAt(int x, int z) {
        int ix = Math.floorDiv(x - x0, cell), iz = Math.floorDiv(z - z0, cell);
        if (ix < 0 || iz < 0 || ix >= w || iz >= h) return biomeNames.get(0);
        return biomeNames.get(biome[iz * w + ix] & 0xFF);
    }

    public List<String> biomeNames() {
        return biomeNames;
    }

    // ---- Sites and roads ---------------------------------------------------------------

    @Nullable
    public Site site(String id) {
        return sites.get(id);
    }

    public Collection<Site> sites() {
        return sites.values();
    }

    /** Road pieces that pass within reach of the 32x32 cell holding (x, z). */
    public List<Segment> roadsNear(int x, int z) {
        return roadIndex.getOrDefault(ChunkPos.asLong(Math.floorDiv(x, ROAD_CELL), Math.floorDiv(z, ROAD_CELL)), List.of());
    }

    public List<Lamp> lampsIn(ChunkPos chunk) {
        return lampIndex.getOrDefault(chunk.toLong(), List.of());
    }
}
