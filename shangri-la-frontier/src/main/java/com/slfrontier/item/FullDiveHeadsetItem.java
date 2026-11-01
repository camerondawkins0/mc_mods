package com.slfrontier.item;

import com.slfrontier.world.FrontierTravel;
import com.slfrontier.world.Frontier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The full-dive VR headset. Hold use for a moment to log in to Shangri-La Frontier,
 * or, while inside, to log out back to where you started.
 */
public class FullDiveHeadsetItem extends TooltipItem {
    public FullDiveHeadsetItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 30;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPYGLASS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            if (Frontier.isIn(player)) {
                FrontierTravel.logout(player);
            } else {
                FrontierTravel.login(player);
            }
            player.getCooldowns().addCooldown(stack, 60);
        }
        return stack;
    }
}
