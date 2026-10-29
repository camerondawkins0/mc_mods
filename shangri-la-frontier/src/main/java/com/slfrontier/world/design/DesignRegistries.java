package com.slfrontier.world.design;

import com.mojang.serialization.MapCodec;
import com.slfrontier.SLFrontier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Worldgen types behind the designed Frontier map. */
public final class DesignRegistries {
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, SLFrontier.MODID);
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, SLFrontier.MODID);
    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, SLFrontier.MODID);
    private static final DeferredRegister<StructurePlacementType<?>> PLACEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, SLFrontier.MODID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, SLFrontier.MODID);

    static {
        DENSITY_FUNCTIONS.register("design", () -> DesignDensity.MAP_CODEC);
        BIOME_SOURCES.register("designed", () -> DesignBiomeSource.CODEC);
    }

    public static final DeferredHolder<StructureType<?>, StructureType<DesignedStructure>> DESIGNED_STRUCTURE =
            STRUCTURE_TYPES.register("designed", () -> () -> DesignedStructure.CODEC);
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<DesignedPlacement>> DESIGNED_PLACEMENT =
            PLACEMENTS.register("designed", () -> () -> DesignedPlacement.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> DESIGNED_PIECE =
            PIECES.register("designed", () -> (StructurePieceType.StructureTemplateType) DesignedPiece::new);

    private DesignRegistries() {
    }

    public static void register(IEventBus modBus) {
        DENSITY_FUNCTIONS.register(modBus);
        BIOME_SOURCES.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        PLACEMENTS.register(modBus);
        PIECES.register(modBus);
    }
}
