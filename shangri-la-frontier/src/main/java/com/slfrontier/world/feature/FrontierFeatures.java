package com.slfrontier.world.feature;

import com.slfrontier.SLFrontier;
import com.slfrontier.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Frontier's landmark features: colossal trees in the Rampant Forest, prismatic crystal
 * spires in the Grotto, natural stone arches, the tilted monolith panels of the Divinity
 * ruins and gnarled dead trees. Each stays within 15 blocks of its origin so it never
 * writes outside the chunks worldgen allows.
 */
public final class FrontierFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, SLFrontier.MODID);

    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> GIANT_TREE =
            FEATURES.register("giant_tree", () -> new Simple(FrontierFeatures::giantTree));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> CRYSTAL_SPIRE =
            FEATURES.register("crystal_spire", () -> new Simple(FrontierFeatures::crystalSpires));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> ROCK_ARCH =
            FEATURES.register("rock_arch", () -> new Simple(FrontierFeatures::rockArch));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> DIVINITY_MONOLITH =
            FEATURES.register("divinity_monolith", () -> new Simple(FrontierFeatures::monoliths));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> DEAD_TREE =
            FEATURES.register("dead_tree", () -> new Simple(FrontierFeatures::deadTree));

    private static final int REACH = 15;

    private FrontierFeatures() {
    }

    @FunctionalInterface
    private interface Placer {
        boolean place(WorldGenLevel level, BlockPos origin, RandomSource random);
    }

    private static final class Simple extends Feature<NoneFeatureConfiguration> {
        private final Placer placer;

        Simple(Placer placer) {
            super(NoneFeatureConfiguration.CODEC);
            this.placer = placer;
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            return placer.place(ctx.level(), ctx.origin(), ctx.random());
        }
    }

    // ---- Helpers ----------------------------------------------------------------------

    private static void set(WorldGenLevel level, BlockPos origin, BlockPos pos, BlockState state) {
        if (Math.abs(pos.getX() - origin.getX()) > REACH || Math.abs(pos.getZ() - origin.getZ()) > REACH) return;
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) return;
        level.setBlock(pos, state, 2);
    }

    /** Places only into air, plants and leaves (so features don't eat each other's terrain). */
    private static void soft(WorldGenLevel level, BlockPos origin, BlockPos pos, BlockState state) {
        if (Math.abs(pos.getX() - origin.getX()) > REACH || Math.abs(pos.getZ() - origin.getZ()) > REACH) return;
        BlockState here = level.getBlockState(pos);
        if (here.isAir() || here.canBeReplaced() || here.is(BlockTags.LEAVES)) set(level, origin, pos, state);
    }

    private static boolean solidGround(WorldGenLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(BlockTags.DIRT) || below.is(BlockTags.BASE_STONE_OVERWORLD) || below.is(Blocks.SAND)
                || below.is(Blocks.GRAVEL) || below.is(Blocks.TUFF) || below.is(Blocks.COARSE_DIRT);
    }

    private static int groundY(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
    }

    private static BlockState leaves(BlockState leaves) {
        return leaves.hasProperty(LeavesBlock.PERSISTENT) ? leaves.setValue(LeavesBlock.PERSISTENT, true) : leaves;
    }

    // ---- Giant trees --------------------------------------------------------------------

    private static boolean giantTree(WorldGenLevel level, BlockPos origin, RandomSource random) {
        if (!solidGround(level, origin)) return false;
        boolean birch = random.nextInt(4) == 0;
        BlockState wood = (birch ? Blocks.BIRCH_WOOD : Blocks.OAK_WOOD).defaultBlockState();
        BlockState leaf = leaves((birch ? Blocks.BIRCH_LEAVES : random.nextInt(3) == 0 ? Blocks.AZALEA_LEAVES : Blocks.OAK_LEAVES)
                .defaultBlockState());
        int height = 22 + random.nextInt(12);
        float trunk = 2.2f + random.nextFloat() * 0.8f;
        int cx = origin.getX(), cz = origin.getZ(), y0 = origin.getY();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = -2; y < height; y++) {
            float r = trunk * (1 - 0.3f * Math.max(0, y) / height) + Math.max(0, 3 - y) * 0.5f;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (dx * dx + dz * dz <= r * r) set(level, origin, p.set(cx + dx, y0 + y, cz + dz), wood);
                }
            }
        }
        // Buttress roots
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + random.nextDouble() * 0.5;
            int len = 4 + random.nextInt(4);
            for (int i = 0; i < len; i++) {
                int x = cx + (int) Math.round(Math.cos(a) * (trunk + i));
                int z = cz + (int) Math.round(Math.sin(a) * (trunk + i));
                int gy = groundY(level, x, z);
                for (int y = gy - 1; y <= y0 + Math.max(0, 2 - i / 2); y++) set(level, origin, p.set(x, y, z), wood);
            }
        }
        // Great branches, each ending in a leaf blob, plus the crown
        canopy(level, origin, random, cx, y0 + height, cz, 7 + random.nextInt(3), leaf);
        for (int k = 0; k < 5; k++) {
            double a = k * Math.PI * 2 / 5 + random.nextDouble() * 0.6;
            int by = y0 + (int) (height * (0.55 + random.nextDouble() * 0.3));
            int len = 6 + random.nextInt(4);
            int x = cx, z = cz, y = by;
            for (int i = 0; i < len; i++) {
                x = cx + (int) Math.round(Math.cos(a) * i);
                z = cz + (int) Math.round(Math.sin(a) * i);
                y = by + i / 3;
                set(level, origin, p.set(x, y, z), wood);
                set(level, origin, p.set(x, y + 1, z), wood);
            }
            canopy(level, origin, random, x, y + 1, z, 4 + random.nextInt(2), leaf);
        }
        return true;
    }

    private static void canopy(WorldGenLevel level, BlockPos origin, RandomSource random, int cx, int cy, int cz, int r, BlockState leaf) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -r / 2; dy <= r / 2 + 1; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 2.6);
                    if (d <= r && (d < r - 1 || random.nextFloat() < 0.6f)) soft(level, origin, p.set(cx + dx, cy + dy, cz + dz), leaf);
                }
            }
        }
    }

    // ---- Prismatic crystal spires ---------------------------------------------------------

    private static boolean crystalSpires(WorldGenLevel level, BlockPos origin, RandomSource random) {
        if (!solidGround(level, origin)) return false;
        BlockState crystal = ModBlocks.PRISMATIC_CRYSTAL.get().defaultBlockState();
        BlockState cluster = ModBlocks.PRISMATIC_CLUSTER.get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
        BlockState base = Blocks.CALCITE.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int spires = 3 + random.nextInt(4);
        for (int s = 0; s < spires; s++) {
            int x0 = origin.getX() + (s == 0 ? 0 : random.nextInt(13) - 6);
            int z0 = origin.getZ() + (s == 0 ? 0 : random.nextInt(13) - 6);
            int y0 = groundY(level, x0, z0) - 1;
            int height = (s == 0 ? 16 : 6) + random.nextInt(s == 0 ? 10 : 10);
            float r0 = s == 0 ? 2.4f : 1.2f + random.nextFloat();
            double tiltA = random.nextDouble() * Math.PI * 2, tilt = random.nextDouble() * 0.35;
            for (int y = 0; y <= height; y++) {
                float r = r0 * (1 - (float) y / (height + 2));
                double ox = Math.cos(tiltA) * tilt * y, oz = Math.sin(tiltA) * tilt * y;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if ((dx - ox % 1) * (dx - ox % 1) + (dz - oz % 1) * (dz - oz % 1) <= r * r + 0.3) {
                            set(level, origin, p.set(x0 + dx + (int) ox, y0 + y, z0 + dz + (int) oz), crystal);
                        }
                    }
                }
                if (y == height) soft(level, origin, p.set(x0 + (int) ox, y0 + y + 1, z0 + (int) oz), cluster);
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dz * dz <= 5 && random.nextBoolean()) set(level, origin, p.set(x0 + dx, y0, z0 + dz), base);
                }
            }
        }
        return true;
    }

    // ---- Rock arches -----------------------------------------------------------------------

    private static boolean rockArch(WorldGenLevel level, BlockPos origin, RandomSource random) {
        if (!solidGround(level, origin)) return false;
        double angle = random.nextDouble() * Math.PI;
        double ax = Math.cos(angle), az = Math.sin(angle);
        int span = 6 + random.nextInt(7);  // half-span
        int rise = 6 + random.nextInt(8);
        int thick = 2 + random.nextInt(2);
        BlockState[] rock = {Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState(),
                Blocks.STONE.defaultBlockState()};
        BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int baseY = origin.getY() - 1;
        for (int i = 0; i <= 64; i++) {
            double t = Math.PI * i / 64;
            double along = -Math.cos(t) * span;
            double height = Math.sin(t) * rise;
            int x = origin.getX() + (int) Math.round(ax * along), z = origin.getZ() + (int) Math.round(az * along);
            int y = baseY + (int) Math.round(height);
            float w = thick + (float) (1 - Math.sin(t)) * 1.5f;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        if (dx * dx + dz * dz + dy * dy > w * w) continue;
                        BlockState s = rock[Math.floorMod(y + dy + dx, rock.length)];
                        set(level, origin, p.set(x + dx, y + dy, z + dz), dy >= (int) w - 1 && random.nextFloat() < 0.5f ? moss : s);
                    }
                }
            }
            // feet reach down to the ground
            if (i < 8 || i > 56) {
                int gy = groundY(level, x, z);
                for (int yy = gy - 2; yy < y; yy++) set(level, origin, p.set(x, yy, z), rock[0]);
            }
        }
        return true;
    }

    // ---- Divinity monoliths -------------------------------------------------------------

    private static boolean monoliths(WorldGenLevel level, BlockPos origin, RandomSource random) {
        if (!solidGround(level, origin)) return false;
        BlockState slab = Blocks.DEEPSLATE_TILES.defaultBlockState(), dark = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState line = Blocks.SEA_LANTERN.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int panels = 2 + random.nextInt(4);
        for (int n = 0; n < panels; n++) {
            int px = origin.getX() + random.nextInt(17) - 8, pz = origin.getZ() + random.nextInt(17) - 8;
            int py = groundY(level, px, pz) + (n == 0 ? 0 : random.nextInt(6));
            int w = 3 + random.nextInt(3), h = 10 + random.nextInt(9);
            double yaw = random.nextDouble() * Math.PI, lean = (random.nextDouble() - 0.5) * 0.7;
            double ux = Math.cos(yaw), uz = Math.sin(yaw), nx = -uz, nz = ux;
            for (int v = 0; v < h; v++) {
                for (int u = -w; u <= w; u++) {
                    double off = v * lean;
                    int x = px + (int) Math.round(ux * u + nx * off);
                    int z = pz + (int) Math.round(uz * u + nz * off);
                    boolean edge = Math.abs(u) == w || v == 0 || v == h - 1;
                    boolean circuit = !edge && (u == 0 && v % 5 != 0 || v % 5 == 2 && Math.abs(u) <= w - 1 && (u + v) % 3 != 0);
                    set(level, origin, p.set(x, py + v, z), circuit ? line : edge ? dark : slab);
                    if (random.nextFloat() < 0.12f) {
                        BlockPos side = p.immutable().relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
                        Direction face = Direction.getNearest(p.getX() - side.getX(), 0, p.getZ() - side.getZ());
                        var prop = VineBlock.getPropertyForFace(face);
                        if (prop != null && level.isEmptyBlock(side)) {
                            for (int d = 0; d < 1 + random.nextInt(4) && level.isEmptyBlock(side.below(d)); d++) {
                                soft(level, origin, side.below(d), Blocks.VINE.defaultBlockState().setValue(prop, true));
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    // ---- Dead trees ----------------------------------------------------------------------

    private static boolean deadTree(WorldGenLevel level, BlockPos origin, RandomSource random) {
        if (!solidGround(level, origin)) return false;
        BlockState wood = Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState();
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int height = 6 + random.nextInt(7);
        double x = origin.getX(), z = origin.getZ();
        for (int y = 0; y < height; y++) {
            set(level, origin, p.set((int) Math.round(x), origin.getY() + y, (int) Math.round(z)), wood);
            if (y < 2) {
                set(level, origin, p.set((int) Math.round(x) + 1, origin.getY() + y, (int) Math.round(z)), wood);
            }
            x += (random.nextDouble() - 0.5) * 0.6;
            z += (random.nextDouble() - 0.5) * 0.6;
        }
        for (int k = 0; k < 3 + random.nextInt(3); k++) {
            double a = random.nextDouble() * Math.PI * 2;
            double bx = x, bz = z, by = origin.getY() + height * (0.4 + random.nextDouble() * 0.5);
            Direction.Axis axis = Math.abs(Math.cos(a)) > 0.7 ? Direction.Axis.X : Math.abs(Math.sin(a)) > 0.7 ? Direction.Axis.Z : Direction.Axis.Y;
            for (int i = 0; i < 3 + random.nextInt(4); i++) {
                bx += Math.cos(a);
                bz += Math.sin(a);
                by += random.nextInt(3) == 0 ? 1 : 0;
                a += (random.nextDouble() - 0.5) * 0.8;
                set(level, origin, p.set((int) Math.round(bx), (int) by, (int) Math.round(bz)), log.setValue(RotatedPillarBlock.AXIS, axis));
            }
        }
        return true;
    }

    static float clamp(float v) {
        return Mth.clamp(v, 0, 1);
    }
}
