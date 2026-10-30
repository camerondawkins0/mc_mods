package com.slfrontier.world;

import com.slfrontier.SLFrontier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/** Keys for the Frontier dimension, its biomes and structures. */
public final class Frontier {
    /** The designed continent. (The noise-generated Frontier of older versions was "slfrontier:frontier".) */
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, SLFrontier.id("the_frontier"));

    public static final ResourceKey<Biome> FASTERA_PLAINS = biome("fastera_plains");
    public static final ResourceKey<Biome> DIREMARSH_WASTE = biome("diremarsh_waste");
    public static final ResourceKey<Biome> PRISMATIC_FOREST_GROTTO = biome("prismatic_forest_grotto");
    public static final ResourceKey<Biome> HIDDEN_GARDEN = biome("hidden_garden");
    public static final ResourceKey<Biome> NIGHTSLAYER_WOODS = biome("nightslayer_woods");
    public static final ResourceKey<Biome> RAMPANT_FOREST = biome("rampant_forest");
    public static final ResourceKey<Biome> LIGHTLESS_BARRENS = biome("lightless_barrens");
    public static final ResourceKey<Biome> THIRDREMA_HIGHLANDS = biome("thirdrema_highlands");

    private Frontier() {
    }

    public static boolean isIn(Entity entity) {
        return entity.level().dimension() == LEVEL;
    }

    public static boolean isFrontier(Level level) {
        return level.dimension() == LEVEL;
    }

    private static ResourceKey<Biome> biome(String name) {
        return ResourceKey.create(Registries.BIOME, SLFrontier.id(name));
    }
}
