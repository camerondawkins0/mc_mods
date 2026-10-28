package com.slfrontier.world;

import com.slfrontier.SLFrontier;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The hand-placed landmarks of the Frontier: its towns and the story's set pieces. Each has
 * a planned location near fixed coordinates (chosen once, from terrain noise, preferring flat
 * dry land in the right biome), and is built the first time it is needed or a player wanders
 * close. Building flattens a pad, blends the edges into the surrounding terrain and reads the
 * data markers (structure blocks) left in the template.
 */
public final class Landmarks {
    public enum Site {
        /** Firstia, the starting town. Its id stays "fastera" so older worlds keep their town. */
        FIRSTIA("fastera", "town/firstia", 0, 0, Frontier.FASTERA_PLAINS, true),
        SERPENT_BRIDGE("serpent_bridge", "landmark/serpent_bridge", 460, 40, Frontier.RAMPANT_FOREST, false),
        SECONDIL("secondil", "town/secondil", 880, 60, Frontier.RAMPANT_FOREST, true),
        THIRDREMA("thirdrema", "town/thirdrema", 1600, 560, null, true),
        RABITUZA("rabituza", "town/rabituza", Frontier.RABITUZA_X, Frontier.RABITUZA_Z, null, true);

        public final String id;
        public final ResourceLocation template;
        public final int x, z;
        @Nullable
        public final ResourceKey<Biome> biome;
        public final boolean town;

        Site(String id, String template, int x, int z, @Nullable ResourceKey<Biome> biome, boolean town) {
            this.id = id;
            this.template = SLFrontier.id(template);
            this.x = x;
            this.z = z;
            this.biome = biome;
            this.town = town;
        }

        public String nameKey() {
            return "waypoint.slfrontier." + id;
        }
    }

    /** Players this close to an unbuilt site trigger its construction. */
    private static final int BUILD_RADIUS = 224;
    private static final int MARGIN = 14;

    private Landmarks() {
    }

    /** The site's spawn point, building it first if needed. */
    public static BlockPos ensure(ServerLevel level, Site site) {
        TownData data = TownData.get(level);
        BlockPos built = data.built(site.id);
        if (built != null) return built;
        BlockPos spot = plan(level, site);
        BlockPos spawn = build(level, site, spot);
        data.setBuilt(site.id, spawn);
        registerWaypoints(level);
        return spawn;
    }

    @Nullable
    public static BlockPos built(ServerLevel level, Site site) {
        return TownData.get(level).built(site.id);
    }

    /** Where the site is (or will be): cheap, never builds or waits for anything. */
    public static BlockPos where(ServerLevel level, Site site) {
        BlockPos built = built(level, site);
        return built != null ? built : estimate(level, site);
    }

    @Nullable
    public static BlockPos marker(ServerLevel level, Site site, String marker) {
        return TownData.get(level).marker(site.id, marker);
    }

    /** Builds any site a player has wandered close to. Runs every couple of seconds. */
    public static void tick(ServerLevel level) {
        collect(level);
        TownData data = TownData.get(level);
        for (Site site : Site.values()) {
            BlockPos spot = data.planned(site.id);
            if (data.built(site.id) != null || site == Site.RABITUZA || spot == null) continue;
            for (ServerPlayer p : level.players()) {
                if (p.blockPosition().distSqr(spot) < (double) BUILD_RADIUS * BUILD_RADIUS) {
                    ensure(level, site);
                    break;
                }
            }
        }
    }

    /** Towns are fast-travel waypoints. */
    public static void registerWaypoints(ServerLevel level) {
        TownData data = TownData.get(level);
        Waypoints all = Waypoints.get(level);
        for (Site site : Site.values()) {
            BlockPos pos = data.built(site.id);
            if (site.town && pos != null) all.put(new Waypoints.Waypoint(site.id, site.nameKey(), pos, Waypoints.Kind.TOWN));
        }
    }

    // ---- Planning ----------------------------------------------------------------------

    /** Background searches for sites nobody has needed yet. */
    private static final Map<String, CompletableFuture<BlockPos>> PENDING = new ConcurrentHashMap<>();

    /**
     * Where the search for a site starts. Firstia sits at the origin; the mainland sites keep
     * their distances from wherever Firstia ended up; Rabituza is far away on its own.
     */
    private static BlockPos anchor(ServerLevel level, Site site) {
        if (site == Site.FIRSTIA || site == Site.RABITUZA) return new BlockPos(site.x, 0, site.z);
        BlockPos firstia = plan(level, Site.FIRSTIA);
        return new BlockPos(firstia.getX() + site.x, 0, firstia.getZ() + site.z);
    }

    /**
     * Picks (and remembers) the site's location: the flattest dry spot in the preferred biome
     * near its anchor. Uses only the noise generator, so nothing is generated or loaded.
     */
    public static BlockPos plan(ServerLevel level, Site site) {
        TownData data = TownData.get(level);
        BlockPos planned = data.planned(site.id);
        if (planned != null) return planned;
        CompletableFuture<BlockPos> pending = PENDING.remove(site.id);
        BlockPos result = pending != null ? pending.join()
                : search(level, site, anchor(level, site), templateSize(level, site), taken(level));
        data.setPlanned(site.id, result);
        return result;
    }

    /**
     * Starts planning every site in the background, so a first visit never stalls the server.
     * The searches run one after another so each can keep clear of the sites planned before it.
     */
    public static void planAhead(ServerLevel level) {
        TownData data = TownData.get(level);
        plan(level, Site.FIRSTIA);
        List<int[]> taken = new CopyOnWriteArrayList<>(taken(level));
        CompletableFuture<?> previous = CompletableFuture.completedFuture(null);
        for (Site site : Site.values()) {
            if (data.planned(site.id) != null || PENDING.containsKey(site.id)) continue;
            BlockPos anchor = anchor(level, site);
            Vec3i size = templateSize(level, site);
            CompletableFuture<BlockPos> next = previous.thenApplyAsync(ignored -> {
                BlockPos spot = search(level, site, anchor, size, taken);
                taken.add(new int[]{spot.getX(), spot.getZ(), size.getX() / 2, size.getZ() / 2});
                return spot;
            }, Util.backgroundExecutor());
            PENDING.put(site.id, next);
            previous = next;
        }
    }

    /** Footprints (centre x, z and half sizes) of every site planned so far. */
    private static List<int[]> taken(ServerLevel level) {
        TownData data = TownData.get(level);
        List<int[]> taken = new ArrayList<>();
        for (Site site : Site.values()) {
            BlockPos spot = data.planned(site.id);
            if (spot == null) continue;
            Vec3i size = templateSize(level, site);
            taken.add(new int[]{spot.getX(), spot.getZ(), size.getX() / 2, size.getZ() / 2});
        }
        return taken;
    }

    /** Stores background plans that have finished (server thread). */
    private static void collect(ServerLevel level) {
        for (var e : PENDING.entrySet()) {
            if (e.getValue().isDone() && PENDING.remove(e.getKey(), e.getValue())) {
                TownData.get(level).setPlanned(e.getKey(), e.getValue().join());
            }
        }
    }

    /** The site's position if it is known, otherwise its anchor (never blocks). */
    private static BlockPos estimate(ServerLevel level, Site site) {
        collect(level);
        BlockPos planned = TownData.get(level).planned(site.id);
        return planned != null ? planned : anchor(level, site);
    }

    private static BlockPos search(ServerLevel level, Site site, BlockPos anchor, Vec3i size, List<int[]> taken) {
        int half = Math.max(size.getX(), size.getZ()) / 2;
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        int sea = level.getSeaLevel();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        // First look only in the right biome (biome checks are cheap, heights are not), then anywhere.
        for (int pass = 0; pass < 2 && (best == null || pass == 0); pass++) {
            if (pass == 1 && site.biome == null) break;
            search:
            for (int r = 0; r <= 384; r += 48) {
                for (int i = -r; i <= r; i += 48) {
                    int[][] candidates = r == 0 ? new int[][]{{anchor.getX(), anchor.getZ()}}
                            : new int[][]{{anchor.getX() + i, anchor.getZ() - r}, {anchor.getX() + i, anchor.getZ() + r},
                            {anchor.getX() - r, anchor.getZ() + i}, {anchor.getX() + r, anchor.getZ() + i}};
                    for (int[] c : candidates) {
                        if (overlaps(c, size, taken)) continue;
                        int[][] offsets = {{0, 0}, {-half, -half}, {half, -half}, {-half, half}, {half, half}};
                        if (pass == 0 && site.biome != null && !inBiome(level, site.biome, c, offsets, sea + 8)) continue;
                        int[] heights = new int[5];
                        boolean wet = false;
                        for (int k = 0; k < 5; k++) {
                            heights[k] = generator.getBaseHeight(c[0] + offsets[k][0], c[1] + offsets[k][1],
                                    Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                            if (heights[k] <= sea + 1) wet = true;
                        }
                        if (wet) continue;
                        // Water on one side (a lake, a river) is fine; a pad standing out in the sea is not.
                        int reach = half + MARGIN * 2, shores = 0;
                        for (int[] o : new int[][]{{reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}}) {
                            if (generator.getBaseHeight(c[0] + o[0], c[1] + o[1], Heightmap.Types.OCEAN_FLOOR_WG, level, random) <= sea) shores++;
                        }
                        if (shores >= 2) continue;
                        int lo = Arrays.stream(heights).min().orElse(sea), hi = Arrays.stream(heights).max().orElse(sea);
                        double score = (hi - lo) + r / 48.0 + Math.max(0, heights[0] - (sea + 30)) * 0.5;
                        if (score < bestScore) {
                            bestScore = score;
                            best = new BlockPos(c[0], heights[0], c[1]);
                            if (score < 14) break search;
                        }
                    }
                }
            }
        }
        if (best == null) best = new BlockPos(anchor.getX(), sea + 2, anchor.getZ());
        SLFrontier.LOGGER.info("Planned {} at {} (score {})", site.id, best, bestScore);
        return best;
    }

    /** Whether a footprint centred on {@code c} would crowd (or sit on) another site. */
    private static boolean overlaps(int[] c, Vec3i size, List<int[]> taken) {
        for (int[] t : taken) {
            if (Math.abs(c[0] - t[0]) < size.getX() / 2 + t[2] + 2 * MARGIN
                    && Math.abs(c[1] - t[1]) < size.getZ() / 2 + t[3] + 2 * MARGIN) {
                return true;
            }
        }
        return false;
    }

    /** Whether the centre and most of the footprint (points 70% of the way to each corner) lie in the biome. */
    private static boolean inBiome(ServerLevel level, ResourceKey<Biome> biome, int[] c, int[][] offsets, int y) {
        for (int[] o : offsets) {
            if (!biomeAt(level, c[0] + o[0] * 7 / 10, y, c[1] + o[1] * 7 / 10).is(biome)) return false;
        }
        return true;
    }

    private static Holder<Biome> biomeAt(ServerLevel level, int x, int y, int z) {
        var generator = level.getChunkSource().getGenerator();
        var sampler = level.getChunkSource().randomState().sampler();
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
    }

    /** The site's footprint (its template's size). */
    public static Vec3i size(ServerLevel level, Site site) {
        return templateSize(level, site);
    }

    private static Vec3i templateSize(ServerLevel level, Site site) {
        return level.getStructureManager().get(site.template).map(StructureTemplate::getSize).orElse(new Vec3i(64, 32, 64));
    }

    // ---- Building ----------------------------------------------------------------------

    private static BlockPos build(ServerLevel level, Site site, BlockPos spot) {
        Optional<StructureTemplate> found = level.getStructureManager().get(site.template);
        if (found.isEmpty()) {
            SLFrontier.LOGGER.error("Missing landmark template {}", site.template);
            return spot.above();
        }
        StructureTemplate template = found.get();
        Vec3i size = template.getSize();
        int x0 = spot.getX() - size.getX() / 2, z0 = spot.getZ() - size.getZ() / 2;
        // Generate the ground first, then settle on its median height. Freshly generated chunks
        // hold their block entities (beehives, buried chests) as pending tags; load them now so
        // overwriting those blocks removes them cleanly.
        for (int cx = (x0 - MARGIN) >> 4; cx <= (x0 + size.getX() + MARGIN) >> 4; cx++) {
            for (int cz = (z0 - MARGIN) >> 4; cz <= (z0 + size.getZ() + MARGIN) >> 4; cz++) {
                for (BlockPos pos : List.copyOf(level.getChunk(cx, cz).getBlockEntitiesPos())) level.getBlockEntity(pos);
            }
        }
        int[] samples = new int[25];
        int k = 0;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                samples[k++] = surface(level, x0 + size.getX() * i / 4, z0 + size.getZ() * j / 4);
            }
        }
        Arrays.sort(samples);
        int base = Math.max(samples[12], level.getSeaLevel() + 1);
        StructurePlaceSettings settings = new StructurePlaceSettings().addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        // Templates that reach below the surface (the Serpent Bridge's gorge) mark their ground layer.
        int groundLayer = 0;
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, Blocks.STRUCTURE_BLOCK)) {
            if (info.nbt() != null && "ground".equals(info.nbt().getString("metadata"))) groundLayer = info.pos().getY();
        }
        terraform(level, x0, z0, size.getX(), size.getZ(), base, size.getY() - groundLayer);
        BlockPos origin = new BlockPos(x0, base - 1 - groundLayer, z0);
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
        BlockPos spawn = new BlockPos(spot.getX(), base, spot.getZ());
        TownData data = TownData.get(level);
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK)) {
            CompoundTag nbt = info.nbt();
            String name = nbt == null ? "" : nbt.getString("metadata");
            if (name.isEmpty()) continue;
            data.setMarker(site.id, name, info.pos());
            if (name.equals("spawn")) spawn = info.pos();
        }
        SLFrontier.LOGGER.info("Built {} at {} (base y {})", site.id, origin, base);
        return spawn;
    }

    /** Y of the top block of natural ground in a column, ignoring trees, plants and water. */
    private static int ground(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        while (p.getY() > level.getMinBuildHeight()) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES) && !s.canBeReplaced() && !s.is(Blocks.BAMBOO)
                    && !s.is(BlockTags.BEEHIVES)
                    && !s.is(Blocks.MUSHROOM_STEM) && !s.is(Blocks.RED_MUSHROOM_BLOCK) && !s.is(Blocks.BROWN_MUSHROOM_BLOCK)) {
                return p.getY();
            }
            p.move(0, -1, 0);
        }
        return p.getY();
    }

    private static int surface(ServerLevel level, int x, int z) {
        return ground(level, x, z) + 1;
    }

    /**
     * Levels the footprint to {@code base} and slopes the ground around it back to the natural
     * terrain, so a town on a hillside gets a terrace instead of a wall of dirt. Trees on the
     * worked ground are cleared so nothing is left floating.
     */
    private static void terraform(ServerLevel level, int x0, int z0, int sx, int sz, int base, int height) {
        BlockState stone = Blocks.STONE.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        List<BlockPos> cutLogs = new ArrayList<>();
        for (int x = x0 - MARGIN; x < x0 + sx + MARGIN; x++) {
            for (int z = z0 - MARGIN; z < z0 + sz + MARGIN; z++) {
                int dx = Math.max(0, Math.max(x0 - x, x - (x0 + sx - 1)));
                int dz = Math.max(0, Math.max(z0 - z, z - (z0 + sz - 1)));
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > MARGIN) continue;
                int top = ground(level, x, z);
                double t = Mth.clamp(d / MARGIN, 0, 1);
                t = t * t * (3 - 2 * t);
                int target = (int) Math.round(Mth.lerp(t, base - 1, top));
                boolean inside = d == 0;
                // Clear vegetation (and, inside the footprint, everything) above the new ground.
                int clearTop = Math.max(top, target) + (inside ? 48 : 28);
                for (int y = target + 1; y <= clearTop; y++) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir()) continue;
                    if (inside || y <= top || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.canBeReplaced()
                            || s.is(BlockTags.BEEHIVES)) {
                        if (s.is(BlockTags.LOGS) && y > top) cutLogs.add(p.immutable());
                        level.setBlock(p, s.getFluidState().isEmpty() || inside ? air : s, 2);
                    }
                }
                // Fill up to the target, with grass on top.
                for (int y = top + 1; y <= target; y++) {
                    level.setBlock(p.set(x, y, z), y == target ? grass : y > target - 3 ? dirt : stone, 2);
                }
                if (target < top) {
                    p.set(x, target, z);
                    BlockState s = level.getBlockState(p);
                    if (s.is(Blocks.DIRT) || s.is(Blocks.STONE) || s.is(BlockTags.BASE_STONE_OVERWORLD)) level.setBlock(p, grass, 2);
                }
            }
        }
        removeFloatingTrees(level, cutLogs);
    }

    /**
     * Trees whose trunks were cut away at the edge of the pad leave their crowns hanging in the
     * air (giant-tree leaves are persistent, so they never decay). Removes every log-and-leaf
     * cluster touching a cut trunk that no longer stands on anything.
     */
    private static void removeFloatingTrees(ServerLevel level, List<BlockPos> cutLogs) {
        Set<BlockPos> checked = new HashSet<>();
        for (BlockPos cut : cutLogs) {
            for (BlockPos start : BlockPos.betweenClosed(cut.offset(-1, -1, -1), cut.offset(1, 1, 1))) {
                start = start.immutable();
                if (checked.contains(start) || !isTreeBlock(level.getBlockState(start))) continue;
                // Collect the cluster; give up (keep it) if it is grounded or too big to be one crown.
                Set<BlockPos> cluster = new HashSet<>();
                ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
                boolean grounded = false;
                while (!queue.isEmpty() && !grounded) {
                    BlockPos p = queue.poll();
                    if (!cluster.add(p)) continue;
                    if (cluster.size() > 20000) {
                        grounded = true;
                        break;
                    }
                    BlockState below = level.getBlockState(p.below());
                    if (level.getBlockState(p).is(BlockTags.LOGS) && !below.isAir() && !isTreeBlock(below) && !below.canBeReplaced()) {
                        grounded = true;
                        break;
                    }
                    // Leaves often touch only at edges or corners, so follow all 26 neighbours.
                    for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                        if (!cluster.contains(q) && isTreeBlock(level.getBlockState(q))) queue.add(q.immutable());
                    }
                }
                checked.addAll(cluster);
                if (grounded) continue;
                for (BlockPos p : cluster) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static boolean isTreeBlock(BlockState s) {
        return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(Blocks.VINE) || s.is(BlockTags.BEEHIVES);
    }
}
