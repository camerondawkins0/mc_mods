package com.slfrontier.block;

import com.mojang.serialization.MapCodec;
import com.slfrontier.SLFrontier;
import com.slfrontier.data.PlayerData;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.world.Frontier;
import com.slfrontier.world.MapSync;
import com.slfrontier.world.Waypoints;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * A Waypoint Crystal: touch it (use) to attune. Attuned crystals appear on the Frontier
 * map, can be fast-travelled to, and the last one becomes your respawn point.
 */
public class WaypointBlock extends Block {
    public static final MapCodec<WaypointBlock> CODEC = simpleCodec(WaypointBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(4, 3, 4, 12, 8, 12),
            Block.box(5, 9, 5, 11, 16, 11));
    /** Structures whose name a crystal inside them takes, checked in order. */
    private static final List<String> NAMED_STRUCTURES = List.of("wezaemon_tomb", "nightslayer_den", "empire_bee_hive",
            "mud_digger_lair", "divinity_ruins", "shadow_altar");

    public WaypointBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            if (!Frontier.isFrontier(level)) {
                sp.displayClientMessage(Component.translatable("message.slfrontier.waypoint.wrong_world").withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.SUCCESS;
            }
            attune(server, pos, sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void attune(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Waypoints all = Waypoints.get(level);
        String id = Waypoints.crystalId(pos);
        Waypoints.Waypoint wp = all.byId(id);
        if (wp == null) {
            wp = new Waypoints.Waypoint(id, nameFor(level, pos), pos.immutable(), Waypoints.Kind.CRYSTAL);
            all.put(wp);
        }
        PlayerData data = ModAttachments.get(player);
        boolean fresh = data.discoverWaypoint(id);
        data.setRespawnWaypoint(id);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.3, 0.6, 0.3, 0.08);
        if (fresh) {
            MapSync.announceDiscovery(player, wp.nameKey());
        } else {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.4f);
        }
        player.displayClientMessage(Component.translatable("message.slfrontier.waypoint_attuned",
                Component.translatable(wp.nameKey())).withStyle(ChatFormatting.AQUA), true);
        MapSync.invalidate(player);
        ModNetwork.sync(player);
    }

    /** A crystal takes the name of the dungeon it stands in, otherwise of its biome. */
    private static String nameFor(ServerLevel level, BlockPos pos) {
        for (String s : NAMED_STRUCTURES) {
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, SLFrontier.id(s));
            if (level.structureManager().getStructureWithPieceAt(pos, tag).isValid()) return "waypoint.slfrontier." + s;
        }
        Holder<Biome> biome = level.getBiome(pos);
        return biome.unwrapKey().map(k -> "biome." + k.location().getNamespace() + "." + k.location().getPath())
                .orElse("block.slfrontier.waypoint_crystal");
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            Waypoints.get(server).remove(Waypoints.crystalId(pos));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.0,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.03 + random.nextDouble() * 0.02, 0);
        }
    }
}
