package com.slfrontier.world;

import com.slfrontier.SLFrontier;
import com.slfrontier.world.design.FrontierDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The towns, landmarks and dungeons of the Frontier. Each stands at a fixed spot on the designed
 * map (see {@link FrontierDesign}) and is generated with the terrain like any structure; this
 * class knows where they are, where their data markers (structure blocks named "spawn",
 * "arena"...) ended up, and which towns players have discovered.
 */
public final class Landmarks {
    public enum Site {
        /** Firstia, the starting town. Its id stays "fastera" so older saves keep their waypoint. */
        FIRSTIA("fastera", true),
        GOBLIN_CAMP("goblin_camp", false),
        SERPENT_BRIDGE("serpent_bridge", false),
        SECONDIL("secondil", true),
        THIRDREMA("thirdrema", true),
        RABITUZA("rabituza", true),
        NIGHTSLAYER_DEN("nightslayer_den", false),
        WEZAEMON_TOMB("wezaemon_tomb", false),
        EMPIRE_BEE_HIVE("empire_bee_hive", false),
        MUD_DIGGER_LAIR("mud_digger_lair", false),
        SHADOW_ALTAR("shadow_altar", false),
        DIVINITY_RUINS("divinity_ruins", false);

        public final String id;
        public final boolean town;

        Site(String id, boolean town) {
            this.id = id;
            this.town = town;
        }

        public String nameKey() {
            return "waypoint.slfrontier." + id;
        }

        public FrontierDesign.Site design() {
            FrontierDesign.Site site = FrontierDesign.get().site(id);
            if (site == null) throw new IllegalStateException("The Frontier map has no site " + id);
            return site;
        }
    }

    /** Players this close to a town discover it (it becomes a fast-travel waypoint). */
    private static final int DISCOVER_RADIUS = 96;
    private static final Map<Site, Map<String, BlockPos>> MARKERS = new ConcurrentHashMap<>();

    private Landmarks() {
    }

    /** The site's arrival point, with its chunk generated so the site is standing there. */
    public static BlockPos ensure(ServerLevel level, Site site) {
        BlockPos spawn = where(level, site);
        level.getChunk(spawn);
        discover(level, site);
        return spawn;
    }

    /** Where a player arrives at the site: its "spawn" marker, or the middle of its plot. */
    public static BlockPos where(ServerLevel level, Site site) {
        BlockPos spawn = marker(level, site, "spawn");
        return spawn != null ? spawn : site.design().center();
    }

    /** Kept for callers that only care about sites they know exist; every designed site does. */
    public static BlockPos built(ServerLevel level, Site site) {
        return where(level, site);
    }

    @Nullable
    public static BlockPos marker(ServerLevel level, Site site, String marker) {
        return MARKERS.computeIfAbsent(site, s -> readMarkers(level, s)).get(marker);
    }

    /** Marker positions straight from the template, placed at the site's origin. */
    private static Map<String, BlockPos> readMarkers(ServerLevel level, Site site) {
        Map<String, BlockPos> found = new HashMap<>();
        FrontierDesign.Site design = site.design();
        Optional<StructureTemplate> template = level.getStructureManager().get(design.template());
        if (template.isEmpty()) {
            SLFrontier.LOGGER.error("Missing template {} for {}", design.template(), site.id);
            return found;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        for (StructureTemplate.StructureBlockInfo info : template.get().filterBlocks(design.origin(), settings, Blocks.STRUCTURE_BLOCK)) {
            CompoundTag nbt = info.nbt();
            String name = nbt == null ? "" : nbt.getString("metadata");
            if (!name.isEmpty()) found.put(name, info.pos());
        }
        return found;
    }

    /** The template's size, for callers that frame a site (the flyover test). */
    public static Vec3i size(ServerLevel level, Site site) {
        FrontierDesign.Site design = site.design();
        return new Vec3i(design.sizeX(), 0, design.sizeZ());
    }

    /** The middle of the site's plot at ground level. */
    public static BlockPos center(Site site) {
        return site.design().center();
    }

    /** The site whose plot (plus a margin) covers this position, if any. */
    @Nullable
    public static Site at(BlockPos pos, int margin) {
        for (Site site : Site.values()) {
            if (site.design().contains(pos.getX(), pos.getZ(), margin)) return site;
        }
        return null;
    }

    public static boolean discovered(ServerLevel level, Site site) {
        return TownData.get(level).built(site.id) != null;
    }

    /** Remembers that somebody has reached the site; towns then become fast-travel waypoints. */
    public static void discover(ServerLevel level, Site site) {
        TownData data = TownData.get(level);
        if (data.built(site.id) != null) return;
        data.setBuilt(site.id, where(level, site));
        registerWaypoints(level);
    }

    /** Discovers sites players walk up to. Runs every couple of seconds. */
    public static void tick(ServerLevel level) {
        for (Site site : Site.values()) {
            if (discovered(level, site)) continue;
            BlockPos center = center(site);
            for (ServerPlayer p : level.players()) {
                if (p.blockPosition().distSqr(center) < (double) DISCOVER_RADIUS * DISCOVER_RADIUS) {
                    discover(level, site);
                    break;
                }
            }
        }
    }

    /** Discovered towns are fast-travel waypoints. */
    public static void registerWaypoints(ServerLevel level) {
        TownData data = TownData.get(level);
        Waypoints all = Waypoints.get(level);
        for (Site site : Site.values()) {
            if (site.town && data.built(site.id) != null) {
                all.put(new Waypoints.Waypoint(site.id, site.nameKey(), where(level, site), Waypoints.Kind.TOWN));
            }
        }
    }
}
