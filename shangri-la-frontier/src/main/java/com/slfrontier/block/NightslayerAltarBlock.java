package com.slfrontier.block;

import com.slfrontier.entity.WolfVoice;
import com.slfrontier.entity.boss.LycagonEntity;
import com.slfrontier.registry.ModEntities;
import com.slfrontier.world.Frontier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The altar at the heart of the Nightslayer's Den. Touch it after dark to call
 * Lycagon the Nightslayer.
 */
public class NightslayerAltarBlock extends Block {
    public NightslayerAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!Frontier.isFrontier(level)) {
            player.sendOverlayMessage(Component.translatable("message.slfrontier.altar.wrong_world").withStyle(ChatFormatting.GRAY));
            return InteractionResult.CONSUME;
        }
        if (!server.isDarkOutside()) {
            player.sendOverlayMessage(Component.translatable("message.slfrontier.altar.day").withStyle(ChatFormatting.GRAY));
            return InteractionResult.CONSUME;
        }
        if (!server.getEntitiesOfClass(LycagonEntity.class, new AABB(pos).inflate(64)).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.slfrontier.altar.already").withStyle(ChatFormatting.DARK_RED));
            return InteractionResult.CONSUME;
        }
        LycagonEntity lycagon = ModEntities.LYCAGON.get().create(server, EntitySpawnReason.EVENT);
        if (lycagon == null) return InteractionResult.FAIL;
        lycagon.snapTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 6.5, 180f, 0f);
        lycagon.finalizeSpawn(server, server.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        lycagon.configureFor(serverPlayer);
        lycagon.setTarget(player);
        server.addFreshEntity(lycagon);
        server.playSound(null, pos, WolfVoice.HOWL, SoundSource.HOSTILE, 4f, 0.5f);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 6.5, 80, 1.5, 1.5, 1.5, 0.02);
        server.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.slfrontier.lycagon.appears").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SOUL, pos.getX() + random.nextDouble(), pos.getY() + 1.1, pos.getZ() + random.nextDouble(), 0, 0.03, 0);
        }
    }
}
