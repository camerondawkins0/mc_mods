package com.slfrontier.world.design;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Paints the designed roads onto one chunk: packed paths with worn edges, plank bridges with
 * railings where they cross water, and lantern posts along the main roads. Runs before
 * vegetation, so nothing grows on the road.
 */
public class RoadFeature extends Feature<NoneFeatureConfiguration> {
    private static final int DECK_Y = 63;

    public RoadFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        ChunkPos chunk = new ChunkPos(ctx.origin());
        FrontierDesign design = FrontierDesign.get();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = chunk.getMinBlockX() + dx, z = chunk.getMinBlockZ() + dz;
                FrontierDesign.Segment road = null;
                double best = Double.MAX_VALUE;
                for (FrontierDesign.Segment s : design.roadsNear(x, z)) {
                    double d = s.distance(x + 0.5, z + 0.5);
                    if (d < best) {
                        best = d;
                        road = s;
                    }
                }
                if (road == null || best > road.halfWidth()) continue;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                p.set(x, top, z);
                BlockState ground = level.getBlockState(p);
                boolean edge = best > road.halfWidth() - (road.main() ? 1.0 : 0.8);
                if (!ground.getFluidState().isEmpty()) {
                    bridge(level, p, x, z, edge, road.main(), random);
                } else {
                    BlockState surface = surface(ground, edge, road.main(), random);
                    if (surface != null) level.setBlock(p, surface, 2);
                }
                placed = true;
            }
        }
        for (FrontierDesign.Lamp lamp : design.lampsIn(chunk)) {
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lamp.x(), lamp.z()) - 1;
            p.set(lamp.x(), top, lamp.z());
            if (!level.getBlockState(p).getFluidState().isEmpty() || !level.getBlockState(p).isSolid()) continue;
            level.setBlock(p.move(0, 1, 0), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
            level.setBlock(p.move(0, 1, 0), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
            level.setBlock(p.move(0, 1, 0), Blocks.LANTERN.defaultBlockState(), 2);
            placed = true;
        }
        return placed;
    }

    /** What the road is made of here, or null to leave the ground alone. */
    private static BlockState surface(BlockState ground, boolean edge, boolean main, RandomSource random) {
        if (!(ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.TUFF))) {
            return null;
        }
        float r = random.nextFloat();
        if (ground.is(Blocks.MUD)) {
            return (r < 0.6f ? Blocks.PACKED_MUD : Blocks.MUD_BRICKS).defaultBlockState();
        }
        if (ground.is(BlockTags.SAND)) {
            return edge && r < 0.5f ? null : (r < 0.7f ? Blocks.SMOOTH_SANDSTONE : Blocks.SANDSTONE).defaultBlockState();
        }
        if (ground.is(Blocks.GRAVEL) || ground.is(Blocks.TUFF)) {
            return (r < 0.5f ? Blocks.GRAVEL : r < 0.8f ? Blocks.COBBLESTONE : Blocks.COARSE_DIRT).defaultBlockState();
        }
        Block block;
        if (edge) {
            if (r < (main ? 0.25f : 0.55f)) return null;
            block = r < 0.75f ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH;
        } else if (main) {
            block = r < 0.62f ? Blocks.DIRT_PATH : r < 0.8f ? Blocks.GRAVEL : r < 0.92f ? Blocks.COBBLESTONE : Blocks.COARSE_DIRT;
        } else {
            block = r < 0.72f ? Blocks.DIRT_PATH : Blocks.COARSE_DIRT;
        }
        return block.defaultBlockState();
    }

    private static void bridge(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int z, boolean edge, boolean main,
                               RandomSource random) {
        BlockState deck = (main ? Blocks.SPRUCE_PLANKS : Blocks.OAK_PLANKS).defaultBlockState();
        level.setBlock(p.set(x, DECK_Y, z), deck, 2);
        if (edge) {
            level.setBlock(p.set(x, DECK_Y + 1, z), (main ? Blocks.SPRUCE_FENCE : Blocks.OAK_FENCE).defaultBlockState(), 2);
            if (Math.floorMod(x * 31 + z * 17, 5) == 0) {
                // A post down to the riverbed.
                BlockState post = (main ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG).defaultBlockState();
                for (int y = DECK_Y - 1; y > level.getMinBuildHeight(); y--) {
                    BlockState here = level.getBlockState(p.set(x, y, z));
                    if (here.isSolid() && here.getFluidState().isEmpty()) break;
                    level.setBlock(p, post, 2);
                }
            }
        }
    }
}
