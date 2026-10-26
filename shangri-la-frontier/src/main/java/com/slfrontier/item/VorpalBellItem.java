package com.slfrontier.item;

import com.slfrontier.entity.npc.EmulEntity;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.world.Frontier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Rings for Emul. Calls her to your side, or sends her home if she is already with you. */
public class VorpalBellItem extends TooltipItem {
    public VorpalBellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!ModAttachments.get(serverPlayer).flag("emul_companion")) {
                player.displayClientMessage(Component.translatable("message.slfrontier.bell.silent").withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!Frontier.isIn(player)) {
                player.displayClientMessage(Component.translatable("message.slfrontier.outside_frontier").withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
            EmulEntity existing = EmulEntity.findCompanion(serverPlayer, 64);
            if (existing != null && player.isShiftKeyDown()) {
                existing.discard();
                player.displayClientMessage(Component.translatable("message.slfrontier.bell.dismiss").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            } else {
                EmulEntity.summonFor(serverPlayer);
                player.displayClientMessage(Component.translatable("message.slfrontier.bell.summon").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            player.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
