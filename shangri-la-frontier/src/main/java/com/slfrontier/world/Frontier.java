package com.slfrontier.world;

import com.slfrontier.SLFrontier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Keys for the Frontier dimension, its biomes and structures. */
public final class Frontier {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, SLFrontier.id("frontier"));

    public static final ResourceKey<Biome> FASTERA_PLAINS = biome("fastera_plains");
    public static final ResourceKey<Biome> DIREMARSH_WASTE = biome("diremarsh_waste");
    public static final ResourceKey<Biome> PRISMATIC_FOREST_GROTTO = biome("prismatic_forest_grotto");
    public static final ResourceKey<Biome> HIDDEN_GARDEN = biome("hidden_garden");
    public static final ResourceKey<Biome> NIGHTSLAYER_WOODS = biome("nightslayer_woods");

    public static final TagKey<Structure> WEZAEMON_TOMB = structure("wezaemon_tomb");
    public static final TagKey<Structure> NIGHTSLAYER_DEN = structure("nightslayer_den");
    public static final TagKey<Structure> EMPIRE_BEE_HIVE = structure("empire_bee_hive");
    public static final TagKey<Structure> MUD_DIGGER_LAIR = structure("mud_digger_lair");

    /** Rabituza, the hidden nation of the Vorpal Bunnies, sits far from the starting town. */
    public static final int RABITUZA_X = 6000;
    public static final int RABITUZA_Z = 6000;

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

    private static TagKey<Structure> structure(String name) {
        return TagKey.create(Registries.STRUCTURE, SLFrontier.id(name));
    }
}
