package com.slfrontier.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Use with a damaged weapon or tool in the other hand to restore a quarter of its durability. */
public class WhetstoneItem extends TooltipItem {
    public WhetstoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stone = player.getItemInHand(hand);
        ItemStack target = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!target.isDamageableItem() || !target.isDamaged()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.slfrontier.whetstone.nothing").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stone);
        }
        if (level instanceof ServerLevel server) {
            int repair = Math.max(25, target.getMaxDamage() / 4);
            target.setDamageValue(Math.max(0, target.getDamageValue() - repair));
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
            server.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.1, player.getZ(), 10, 0.3, 0.2, 0.3, 0.2);
            player.displayClientMessage(Component.translatable("message.slfrontier.whetstone.done", target.getHoverName())
                    .withStyle(ChatFormatting.GREEN), true);
            stone.consume(1, player);
            player.getCooldowns().addCooldown(this, 20);
        }
        return InteractionResultHolder.sidedSuccess(stone, level.isClientSide);
    }
}
