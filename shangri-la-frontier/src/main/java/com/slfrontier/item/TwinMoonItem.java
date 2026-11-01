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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/**
 * Twin Moon (Sōgengetsu): Waxing and Waning joined into one great blade.
 * Hold use to wind up a crescent cleave; sneak-use to split it back into two blades.
 */
public class TwinMoonItem extends Item implements FrontierWeapon {
    public TwinMoonItem(ToolMaterial tier, int damage, float speed, Properties properties) {
        super(properties.sword(tier, damage, speed));
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
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (!player.getOffhandItem().isEmpty()) {
                if (!level.isClientSide()) {
                    player.sendOverlayMessage(Component.translatable("message.slfrontier.twin_moon.free_hand").withStyle(ChatFormatting.GRAY));
                }
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide()) split(level, player, stack);
            return InteractionResult.SUCCESS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    private void split(Level level, Player player, ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        ItemStack waxing = tag.read("waxing", ItemStack.CODEC, ops).orElseGet(() -> new ItemStack(ModItems.MOONBLADE_WAXING.get()));
        ItemStack waning = tag.read("waning", ItemStack.CODEC, ops).orElseGet(() -> new ItemStack(ModItems.MOONBLADE_WANING.get()));
        player.setItemInHand(InteractionHand.MAIN_HAND, waxing);
        player.setItemInHand(InteractionHand.OFF_HAND, waning);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 1.2f);
        player.sendOverlayMessage(Component.translatable("message.slfrontier.twin_moon.split").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int charged = getUseDuration(stack, entity) - timeLeft;
        if (charged < 12 || !(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) return false;
        float power = Math.min(1f, charged / 30f);
        float damage = SkillExecutor.attack(player) * (1f + power);
        var look = player.getLookAngle();
        for (LivingEntity target : SkillExecutor.around(player, 4.5)) {
            var to = target.position().subtract(player.position()).normalize();
            if (to.x * look.x + to.z * look.z > 0.2) {
                SkillExecutor.strike(player, target, damage, false);
                target.knockback(0.8 * power + 0.3, -to.x, -to.z, target.damageSources().generic(), 0f);
            }
        }
        server.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + look.x * 2, player.getY() + 1, player.getZ() + look.z * 2, 5, 1, 0.2, 1, 0);
        server.sendParticles(ParticleTypes.END_ROD, player.getX() + look.x * 2, player.getY() + 1, player.getZ() + look.z * 2, 30, 1.5, 0.3, 1.5, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 0.6f);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.getCooldowns().addCooldown(stack, 20);
        stack.hurtAndBreak(2, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.slfrontier.greatsword").withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.translatable("tooltip.slfrontier.twin_moon").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("lore.slfrontier.twin_moon").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
