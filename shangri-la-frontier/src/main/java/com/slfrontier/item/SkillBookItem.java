package com.slfrontier.item;

import com.slfrontier.network.ModNetwork;
import com.slfrontier.progression.Progression;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Reading this teaches a skill. The Seiten Skillbook teaches Sky Clear. */
public class SkillBookItem extends TooltipItem {
    private final Skill skill;

    public SkillBookItem(Skill skill, Properties properties) {
        super(properties, true);
        this.skill = skill;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (ModAttachments.get(serverPlayer).unlock(skill)) {
                Progression.announceSkill(serverPlayer, skill);
                ModNetwork.announce(serverPlayer, "announce.slfrontier.skill_learned", skill.nameKey(), 0x8FD8FF);
                stack.consume(1, player);
            } else {
                player.displayClientMessage(Component.translatable("message.slfrontier.skill_known").withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
