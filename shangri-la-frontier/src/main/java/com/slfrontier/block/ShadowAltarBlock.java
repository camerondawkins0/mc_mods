package com.slfrontier.block;

import com.slfrontier.entity.boss.LycagonShadowEntity;
import com.slfrontier.fx.Fx;
import com.slfrontier.registry.ModEntities;
import com.slfrontier.world.Frontier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The broken altar in the Lightless Barrens. Touch it to draw out Lycagon's Shadow, which
 * rises from the dark around it and hunts whoever woke it.
 */
public class ShadowAltarBlock extends Block {
    public ShadowAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (!Frontier.isFrontier(level)) {
            player.displayClientMessage(Component.translatable("message.slfrontier.altar.wrong_world").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        if (!server.getEntitiesOfClass(LycagonShadowEntity.class, new AABB(pos).inflate(64), e -> !e.isClone()).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.slfrontier.shadow_altar.already").withStyle(ChatFormatting.DARK_PURPLE), true);
            return InteractionResult.CONSUME;
        }
        LycagonShadowEntity shadow = ModEntities.LYCAGON_SHADOW.get().create(server);
        if (shadow == null) return InteractionResult.FAIL;
        shadow.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 8.5, 180f, 0f);
        shadow.finalizeSpawn(server, server.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        shadow.setTarget(player);
        server.addFreshEntity(shadow);
        Fx.pillar(server, Vec3.atBottomCenterOf(pos.above()), 1.4f, 16f, 0xFFB070FF, 30);
        Fx.ring(server, Vec3.atBottomCenterOf(pos.above()), 12f, 0xFFB070FF, 24, 0.6f);
        server.playSound(null, pos, SoundEvents.WOLF_HOWL, SoundSource.HOSTILE, 4f, 0.35f);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 80, 2, 1, 2, 0.02);
        player.displayClientMessage(Component.translatable("message.slfrontier.shadow_altar.wakes").withStyle(ChatFormatting.DARK_PURPLE,
                ChatFormatting.BOLD), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + random.nextDouble(), pos.getY() + 1.1, pos.getZ() + random.nextDouble(),
                    0, 0.05, 0);
        }
    }
}
