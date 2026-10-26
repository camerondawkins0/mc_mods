package com.slfrontier.item;

import com.slfrontier.progression.Progression;
import com.slfrontier.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemUtils;

/** Drink to refund every spent stat point. */
public class ReallocationTonicItem extends TooltipItem {
    public ReallocationTonicItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            ModAttachments.get(player).resetStats();
            Progression.applyStats(player);
            player.sendSystemMessage(Component.translatable("message.slfrontier.stats_reset").withStyle(ChatFormatting.LIGHT_PURPLE));
            stack.consume(1, player);
        }
        return stack;
    }
}
