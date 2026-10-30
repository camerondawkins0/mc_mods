package com.slfrontier.world.design;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
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

    /** Templates keep no air above their ground, so first clear whatever terrain or plants stand in the footprint. */
    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        FrontierDesign.Site site = FrontierDesign.get().sites().stream()
                .filter(s -> s.template().toString().equals(templateName)).findFirst().orElse(null);
        if (site != null) {
            BoundingBox b = getBoundingBox();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = Math.max(b.minX(), box.minX()); x <= Math.min(b.maxX(), box.maxX()); x++) {
                for (int z = Math.max(b.minZ(), box.minZ()); z <= Math.min(b.maxZ(), box.maxZ()); z++) {
                    for (int y = site.top() + 1; y <= b.maxY(); y++) {
                        if (!level.getBlockState(p.set(x, y, z)).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        super.postProcess(level, structures, generator, random, box, chunk, pivot);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
    }
}
