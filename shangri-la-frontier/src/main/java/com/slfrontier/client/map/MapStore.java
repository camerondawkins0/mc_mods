package com.slfrontier.client.map;

import com.slfrontier.SLFrontier;
import com.slfrontier.world.Frontier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The player's explored map of the Frontier. Chunks around the player are sampled as they
 * load (top block colour + vanilla-map style relief shading) into 128x128-block tiles,
 * which are saved per world under {@code <game dir>/slfrontier/maps/}.
 */
public final class MapStore {
    public static final int TILE = 128;
    private static final int RESCAN_TICKS = 20 * 30;
    private static final int CHUNKS_PER_TICK = 6;

    public static final class Tile {
        public final int tx, tz;
        /** Packed MapColor ids (id * 4 + brightness), 0 = unexplored. */
        public final byte[] colors = new byte[TILE * TILE];
        public boolean dirty;
        public boolean textureDirty = true;
        int version;

        Tile(int tx, int tz) {
            this.tx = tx;
            this.tz = tz;
        }
    }

    private static final Map<Long, Tile> TILES = new HashMap<>();
    private static final Map<Long, Long> SCANNED = new HashMap<>();
    private static final ArrayDeque<ChunkPos> QUEUE = new ArrayDeque<>();
    private static final java.util.List<long[]> PINS = new java.util.ArrayList<>();
    private static boolean pinsLoaded;
    @Nullable
    private static Path dir;
    private static long ticks;

    private MapStore() {
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    // ---- Lifecycle -------------------------------------------------------------------

    /** Folder for the current world (single player save name or server address). */
    @Nullable
    private static Path worldDir() {
        Minecraft mc = Minecraft.getInstance();
        String key;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            key = "sp_" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        } else if (mc.getCurrentServer() != null) {
            key = "mp_" + mc.getCurrentServer().ip;
        } else {
            return null;
        }
        key = key.replaceAll("[^A-Za-z0-9._-]", "_");
        return mc.gameDirectory.toPath().resolve("slfrontier").resolve("maps").resolve(key);
    }

    public static void onJoin() {
        clearMemory();
        dir = worldDir();
    }

    public static void onLeave() {
        saveAll();
        clearMemory();
        dir = null;
    }

    private static void clearMemory() {
        PINS.clear();
        pinsLoaded = false;
        TILES.clear();
        SCANNED.clear();
        QUEUE.clear();
        MapTextures.releaseAll();
    }

    // ---- Exploration -----------------------------------------------------------------

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.dimension() != Frontier.LEVEL) return;
        ticks++;
        if (dir == null) dir = worldDir();
        if (QUEUE.isEmpty() && ticks % 10 == 0) enqueueAround(mc, level);
        for (int i = 0; i < CHUNKS_PER_TICK && !QUEUE.isEmpty(); i++) {
            ChunkPos cp = QUEUE.poll();
            LevelChunk chunk = level.getChunkSource().getChunk(cp.x, cp.z, false);
            if (chunk != null && !chunk.isEmpty()) {
                scanChunk(level, chunk);
                SCANNED.put(cp.toLong(), ticks);
            }
        }
        if (ticks % (20 * 60) == 0) saveAll();
    }

    private static void enqueueAround(Minecraft mc, ClientLevel level) {
        int r = Math.min(mc.options.getEffectiveRenderDistance(), 12);
        ChunkPos center = mc.player.chunkPosition();
        for (int d = 0; d <= r; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue;
                    ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                    Long at = SCANNED.get(cp.toLong());
                    if (at == null || ticks - at > RESCAN_TICKS) QUEUE.add(cp);
                }
            }
        }
    }

    private static void scanChunk(ClientLevel level, LevelChunk chunk) {
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            // Height of the column to the north, for relief shading.
            int prevHeight = surface(level, chunk, bx + x, bz - 1, pos);
            for (int z = 0; z < 16; z++) {
                int wx = bx + x, wz = bz + z;
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                pos.set(wx, top, wz);
                BlockState state = chunk.getBlockState(pos);
                int y = top;
                // Skip see-through blocks with no map colour (glass, etc.).
                while (y > level.getMinBuildHeight() && state.getMapColor(level, pos) == MapColor.NONE) {
                    pos.setY(--y);
                    state = chunk.getBlockState(pos);
                }
                MapColor color = state.getMapColor(level, pos);
                int brightness;
                FluidState fluid = state.getFluidState();
                if (!fluid.isEmpty() && state.getMapColor(level, pos) == MapColor.WATER) {
                    int depth = 0;
                    BlockPos.MutableBlockPos p2 = pos.mutable();
                    while (depth < 16 && !chunk.getBlockState(p2).getFluidState().isEmpty()) {
                        p2.move(0, -1, 0);
                        depth++;
                    }
                    double d = depth * 0.1 + ((x + z) & 1) * 0.2;
                    brightness = d < 0.5 ? 2 : d > 0.9 ? 0 : 1;
                    color = MapColor.WATER;
                } else {
                    double d = (y - prevHeight) * 0.8 + (((x + z) & 1) - 0.5) * 0.4;
                    brightness = d > 0.6 ? 2 : d < -0.6 ? 0 : 1;
                }
                prevHeight = y;
                set(wx, wz, color == MapColor.NONE ? 0 : (byte) (color.id * 4 + brightness));
            }
        }
    }

    private static int surface(ClientLevel level, LevelChunk chunk, int wx, int wz, BlockPos.MutableBlockPos tmp) {
        LevelChunk c = (wx >> 4) == chunk.getPos().x && (wz >> 4) == chunk.getPos().z ? chunk
                : level.getChunkSource().getChunk(wx >> 4, wz >> 4, false);
        if (c == null || c.isEmpty()) return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, 0);
        return c.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15);
    }

    private static void set(int x, int z, byte value) {
        Tile t = tile(Math.floorDiv(x, TILE), Math.floorDiv(z, TILE), true);
        int i = Math.floorMod(z, TILE) * TILE + Math.floorMod(x, TILE);
        if (t.colors[i] != value) {
            t.colors[i] = value;
            t.dirty = true;
            t.textureDirty = true;
        }
    }

    // ---- Access ----------------------------------------------------------------------

    @Nullable
    public static Tile tile(int tx, int tz, boolean create) {
        long k = key(tx, tz);
        Tile t;
        if (TILES.containsKey(k)) {
            t = TILES.get(k);
        } else {
            t = load(tx, tz);
            TILES.put(k, t);
        }
        if (t == null && create) {
            t = new Tile(tx, tz);
            TILES.put(k, t);
        }
        return t;
    }

    /** Tiles known to exist, without touching the disk for unexplored ones. */
    @Nullable
    public static Tile tileIfExplored(int tx, int tz) {
        return tile(tx, tz, false);
    }

    public static boolean explored(int x, int z) {
        Tile t = tileIfExplored(Math.floorDiv(x, TILE), Math.floorDiv(z, TILE));
        return t != null && t.colors[Math.floorMod(z, TILE) * TILE + Math.floorMod(x, TILE)] != 0;
    }

    // ---- Pins ------------------------------------------------------------------------

    public static java.util.List<long[]> pins() {
        if (!pinsLoaded) loadPins();
        return PINS;
    }

    /** Adds a pin at (x, z), or removes any pin within {@code radius} blocks of it. */
    public static void togglePin(int x, int z, double radius) {
        pins();
        boolean removed = PINS.removeIf(p -> (p[0] - x) * (p[0] - x) + (p[1] - z) * (p[1] - z) <= radius * radius);
        if (!removed) PINS.add(new long[]{x, z});
        savePins();
    }

    private static void loadPins() {
        pinsLoaded = true;
        PINS.clear();
        if (dir == null) return;
        Path f = dir.resolve("pins.txt");
        if (!Files.exists(f)) return;
        try {
            for (String line : Files.readAllLines(f)) {
                String[] parts = line.trim().split(" ");
                if (parts.length == 2) PINS.add(new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])});
            }
        } catch (IOException | NumberFormatException e) {
            SLFrontier.LOGGER.warn("Could not read map pins", e);
        }
    }

    private static void savePins() {
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder();
            for (long[] p : PINS) sb.append(p[0]).append(' ').append(p[1]).append('\n');
            Files.writeString(dir.resolve("pins.txt"), sb.toString());
        } catch (IOException e) {
            SLFrontier.LOGGER.warn("Could not save map pins", e);
        }
    }

    // ---- Disk ------------------------------------------------------------------------

    @Nullable
    private static Tile load(int tx, int tz) {
        if (dir == null) return null;
        Path f = dir.resolve(tx + "_" + tz + ".map");
        if (!Files.exists(f)) return null;
        try (InputStream in = Files.newInputStream(f); DataInputStream data = new DataInputStream(new GZIPInputStream(in))) {
            Tile t = new Tile(tx, tz);
            data.readFully(t.colors);
            return t;
        } catch (IOException e) {
            SLFrontier.LOGGER.warn("Could not read map tile {}", f, e);
            return null;
        }
    }

    public static void saveAll() {
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
            for (Tile t : TILES.values()) {
                if (t == null || !t.dirty) continue;
                Path f = dir.resolve(t.tx + "_" + t.tz + ".map");
                try (OutputStream out = Files.newOutputStream(f); DataOutputStream data = new DataOutputStream(new GZIPOutputStream(out))) {
                    data.write(t.colors);
                }
                t.dirty = false;
            }
        } catch (IOException e) {
            SLFrontier.LOGGER.warn("Could not save the Frontier map", e);
        }
    }
}
