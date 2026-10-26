package com.slfrontier.item;

import com.slfrontier.entity.projectile.HolyWaterEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Throwable holy water. Burns the undead and drenches them, quenching their auras. */
public class HolyWaterItem extends TooltipItem {
    public HolyWaterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.6f, 0.8f);
        if (!level.isClientSide) {
            HolyWaterEntity potion = new HolyWaterEntity(level, player);
            potion.setItem(stack);
            potion.shootFromRotation(player, player.getXRot(), player.getYRot(), -15f, 0.7f, 1f);
            level.addFreshEntity(potion);
        }
        stack.consume(1, player);
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
