package com.slfrontier.entity;

import com.slfrontier.SLFrontier;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.Map;

/**
 * Monster levels by region. The story walks the Frontier from the plains outside Firstia (Act I,
 * levels 1-8) through the Diremarsh, the Nightslayer Woods and the Prismatic Grotto (Act II) to the
 * Thirdrema Highlands, the Hidden Garden, the Northern Peaks and the Lightless Barrens (Acts III and
 * IV, levels 13-21). A monster spawning in a region rolls a level in that region's band, never
 * below its own base level ({@link FrontierMonster#setFrontierLevel} scales its health, damage and
 * experience from there). Regions not listed (the starting plains and forest, the towns) keep the
 * base levels.
 */
public final class LeveledMobRegions {
    /** Biome (path in the slfrontier namespace) to its level band {min, max}. */
    private static final Map<String, int[]> BANDS = Map.ofEntries(
            Map.entry("diremarsh_waste", new int[]{5, 9}),
            Map.entry("mud_digger_lair", new int[]{6, 10}),
            Map.entry("nightslayer_woods", new int[]{8, 12}),
            Map.entry("nightslayer_den", new int[]{9, 12}),
            Map.entry("prismatic_forest_grotto", new int[]{10, 13}),
            Map.entry("empire_bee_hive", new int[]{11, 13}),
            Map.entry("thirdrema_highlands", new int[]{14, 18}),
            Map.entry("thirdrema", new int[]{14, 18}),
            Map.entry("divinity_ruins", new int[]{15, 18}),
            Map.entry("hidden_garden", new int[]{15, 19}),
            Map.entry("wezaemon_tomb", new int[]{16, 19}),
            Map.entry("frontier_peaks", new int[]{17, 21}),
            Map.entry("lightless_barrens", new int[]{18, 22}),
            Map.entry("shadow_altar", new int[]{19, 22}));

    private LeveledMobRegions() {
    }

    /** The level for a monster spawning where it stands. */
    public static int levelFor(FrontierMonster mob, ServerLevelAccessor level) {
        int[] band = level.getBiome(mob.blockPosition()).unwrapKey()
                .filter(key -> key.identifier().getNamespace().equals(SLFrontier.MODID))
                .map(key -> BANDS.get(key.identifier().getPath()))
                .orElse(null);
        if (band == null) return mob.baseLevel();
        int rolled = band[0] + level.getRandom().nextInt(band[1] - band[0] + 1);
        return Math.max(mob.baseLevel(), rolled);
    }
}
