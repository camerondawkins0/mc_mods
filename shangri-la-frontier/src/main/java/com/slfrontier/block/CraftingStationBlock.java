package com.slfrontier.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.slfrontier.crafting.FrontierCrafting;
import com.slfrontier.crafting.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A placeable Frontier crafting station (Forge, Alchemy Table, Workbench). Use it to open
 * the crafting screen for its station; it faces the player who placed it.
 */
public class CraftingStationBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CraftingStationBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Station.CODEC.fieldOf("station").forGetter(CraftingStationBlock::station),
            propertiesCodec()).apply(i, CraftingStationBlock::new));
    /** The alchemy table: a tabletop on legs with flasks on top. */
    private static final VoxelShape TABLE = Shapes.or(Block.box(0, 10, 0, 16, 13, 16), Block.box(1, 0, 1, 15, 10, 15),
            Block.box(3, 13, 3, 13, 16, 13));

    private final Station station;

    public CraftingStationBlock(Station station, Properties properties) {
        super(properties);
        this.station = station;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Station station() {
        return station;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return station == Station.ALCHEMY ? TABLE : Shapes.block();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            FrontierCrafting.openAt(sp, station, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        switch (station) {
            case FORGE -> {
                // Embers and smoke from the forge mouth, the occasional crackle.
                double fx = x + facing.getStepX() * 0.52, fz = z + facing.getStepZ() * 0.52;
                double side = (random.nextDouble() - 0.5) * 0.5;
                level.addParticle(ParticleTypes.SMOKE, fx + facing.getStepZ() * side, y + 0.45, fz + facing.getStepX() * side, 0, 0.01, 0);
                if (random.nextInt(4) == 0) {
                    level.addParticle(ParticleTypes.FLAME, fx + facing.getStepZ() * side, y + 0.3, fz + facing.getStepX() * side, 0, 0, 0);
                }
                if (random.nextInt(8) == 0) {
                    level.playLocalSound(x, y, z, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.6f, 1f, false);
                }
                level.addParticle(ParticleTypes.SMOKE, x, y + 1.05, z, 0, 0.03, 0);
            }
            case ALCHEMY -> {
                if (random.nextInt(3) == 0) {
                    level.addParticle(ParticleTypes.WITCH, x + (random.nextDouble() - 0.5) * 0.5, y + 1.05,
                            z + (random.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
                }
            }
            default -> {
            }
        }
    }
}
