package com.slfrontier.item;

import com.slfrontier.quest.Story;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

import java.util.Optional;

/** Points toward the current story objective. Use it to refresh the target. */
public class FrontierCompassItem extends TooltipItem {
    public FrontierCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            Story.Target target = Story.compassTarget(serverPlayer);
            if (target == null) {
                stack.remove(DataComponents.LODESTONE_TRACKER);
                player.displayClientMessage(Component.translatable("message.slfrontier.compass.none").withStyle(ChatFormatting.GRAY), true);
            } else {
                BlockPos pos = target.pos();
                stack.set(DataComponents.LODESTONE_TRACKER,
                        new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), pos)), false));
                int distance = (int) Math.sqrt(player.blockPosition().distSqr(pos));
                player.displayClientMessage(Component.translatable("message.slfrontier.compass.target",
                        Component.translatable(target.nameKey()), distance).withStyle(ChatFormatting.AQUA), true);
            }
            player.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
