package com.slfrontier.world.design;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.Optional;

/** Places a structure set exactly once: in the chunk where its site starts on the designed map. */
public class DesignedPlacement extends StructurePlacement {
    public static final MapCodec<DesignedPlacement> CODEC =
            Codec.STRING.fieldOf("site").xmap(DesignedPlacement::new, p -> p.site);

    private final String site;

    public DesignedPlacement(String site) {
        super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1f, 0, Optional.empty());
        this.site = site;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
        FrontierDesign.Site s = FrontierDesign.get().site(site);
        return s != null && s.startChunk().equals(new ChunkPos(x, z));
    }

    @Override
    public StructurePlacementType<?> type() {
        return DesignRegistries.DESIGNED_PLACEMENT.get();
    }
}
