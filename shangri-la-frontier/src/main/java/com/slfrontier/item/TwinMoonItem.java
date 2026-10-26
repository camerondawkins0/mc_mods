package com.slfrontier.item;

import com.slfrontier.registry.ModItems;
import com.slfrontier.skill.SkillExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Twin Moon (Sōgengetsu): Waxing and Waning joined into one great blade.
 * Hold use to wind up a crescent cleave; sneak-use to split it back into two blades.
 */
public class TwinMoonItem extends SwordItem implements FrontierWeapon {
    public TwinMoonItem(Tier tier, int damage, float speed, Properties properties) {
        super(tier, properties.attributes(SwordItem.createAttributes(tier, damage, speed)));
    }

    @Override
    public Kind kind() {
        return Kind.GREATSWORD;
    }

    @Override
    public float critBonus() {
        return 0.1f;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (player.isShiftKeyDown()) {
            if (!player.getOffhandItem().isEmpty()) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.slfrontier.twin_moon.free_hand").withStyle(ChatFormatting.GRAY), true);
                }
                return InteractionResultHolder.fail(stack);
            }
            if (!level.isClientSide) split(level, player, stack);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    private void split(Level level, Player player, ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        ItemStack waxing = ItemStack.parse(level.registryAccess(), tag.getCompound("waxing"))
                .orElseGet(() -> new ItemStack(ModItems.MOONBLADE_WAXING.get()));
        ItemStack waning = ItemStack.parse(level.registryAccess(), tag.getCompound("waning"))
                .orElseGet(() -> new ItemStack(ModItems.MOONBLADE_WANING.get()));
        player.setItemInHand(InteractionHand.MAIN_HAND, waxing);
        player.setItemInHand(InteractionHand.OFF_HAND, waning);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 1.2f);
        player.displayClientMessage(Component.translatable("message.slfrontier.twin_moon.split").withStyle(ChatFormatting.AQUA), true);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int charged = getUseDuration(stack, entity) - timeLeft;
        if (charged < 12 || !(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) return;
        float power = Math.min(1f, charged / 30f);
        float damage = SkillExecutor.attack(player) * (1f + power);
        var look = player.getLookAngle();
        for (LivingEntity target : SkillExecutor.around(player, 4.5)) {
            var to = target.position().subtract(player.position()).normalize();
            if (to.x * look.x + to.z * look.z > 0.2) {
                SkillExecutor.strike(player, target, damage, false);
                target.knockback(0.8 * power + 0.3, -to.x, -to.z);
            }
        }
        server.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + look.x * 2, player.getY() + 1, player.getZ() + look.z * 2, 5, 1, 0.2, 1, 0);
        server.sendParticles(ParticleTypes.END_ROD, player.getX() + look.x * 2, player.getY() + 1, player.getZ() + look.z * 2, 30, 1.5, 0.3, 1.5, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 0.6f);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(this, 20);
        stack.hurtAndBreak(2, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.slfrontier.greatsword").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.slfrontier.twin_moon").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("lore.slfrontier.twin_moon").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
