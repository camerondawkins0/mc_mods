package com.slfrontier.block;

import com.slfrontier.entity.WolfVoice;
import com.slfrontier.entity.boss.BossAltar;
import com.slfrontier.entity.boss.LycagonShadowEntity;
import com.slfrontier.quest.QuestStage;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.fx.Fx;
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
import net.minecraft.world.phys.Vec3;

/**
 * The broken altar in the Lightless Barrens. Touch it to draw out Lycagon's Shadow, which
 * rises from the dark around it and hunts whoever woke it. It only answers players whose story
 * has reached the Shadow, and a rematch only once per in-game day (see {@link BossAltar}).
 */
public class ShadowAltarBlock extends Block {
    public ShadowAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (!Frontier.isFrontier(level)) {
            player.sendOverlayMessage(Component.translatable("message.slfrontier.altar.wrong_world").withStyle(ChatFormatting.GRAY));
            return InteractionResult.CONSUME;
        }
        // The shadow only stirs for those the story has brought this far.
        if (ModAttachments.get(serverPlayer).stage().ordinal() < QuestStage.LYCAGON_SHADOW.ordinal()) {
            player.sendOverlayMessage(Component.translatable("message.slfrontier.shadow_altar.not_yet").withStyle(ChatFormatting.GRAY));
            return InteractionResult.CONSUME;
        }
        if (!server.getEntitiesOfClass(LycagonShadowEntity.class, new AABB(pos).inflate(64), e -> !e.isClone()).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.slfrontier.shadow_altar.already").withStyle(ChatFormatting.DARK_PURPLE));
            return InteractionResult.CONSUME;
        }
        LycagonShadowEntity shadow = ModEntities.LYCAGON_SHADOW.get().create(server, EntitySpawnReason.EVENT);
        if (shadow == null) return InteractionResult.FAIL;
        shadow.configureFor(serverPlayer);
        // A rematch (someone who already broke it) answers once per in-game day; the story fight can be retried.
        if (shadow.isRepeatKill()) {
            if (BossAltar.usedToday(serverPlayer, BossAltar.SHADOW)) {
                shadow.discard();
                player.sendOverlayMessage(Component.translatable("message.slfrontier.altar.rested").withStyle(ChatFormatting.GRAY));
                return InteractionResult.CONSUME;
            }
            BossAltar.markUsed(serverPlayer, BossAltar.SHADOW);
        }
        shadow.snapTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 8.5, 180f, 0f);
        shadow.finalizeSpawn(server, server.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        shadow.setTarget(player);
        server.addFreshEntity(shadow);
        Fx.pillar(server, Vec3.atBottomCenterOf(pos.above()), 1.4f, 16f, 0xFFB070FF, 30);
        Fx.ring(server, Vec3.atBottomCenterOf(pos.above()), 12f, 0xFFB070FF, 24, 0.6f);
        server.playSound(null, pos, WolfVoice.HOWL, SoundSource.HOSTILE, 4f, 0.35f);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 80, 2, 1, 2, 0.02);
        player.sendOverlayMessage(Component.translatable("message.slfrontier.shadow_altar.wakes").withStyle(ChatFormatting.DARK_PURPLE,
                ChatFormatting.BOLD));
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
