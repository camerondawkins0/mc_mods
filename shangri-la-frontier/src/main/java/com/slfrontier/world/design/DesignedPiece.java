package com.slfrontier.world.design;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** One whole site template, unrotated, at its designed origin. Data markers are read by Landmarks. */
public class DesignedPiece extends TemplateStructurePiece {
    public DesignedPiece(StructureTemplateManager manager, FrontierDesign.Site site) {
        super(DesignRegistries.DESIGNED_PIECE.get(), 0, manager, site.template(), site.template().toString(), settings(),
                site.origin());
    }

    public DesignedPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(DesignRegistries.DESIGNED_PIECE.get(), tag, manager, id -> settings());
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings().addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
    }
}
