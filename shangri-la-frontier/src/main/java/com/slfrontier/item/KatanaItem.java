package com.slfrontier.item;

import com.slfrontier.network.ModNetwork;
import com.slfrontier.skill.PlayerAnims;
import com.slfrontier.skill.SkillExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

/** A katana. Hold use to draw into an iaijutsu stance, release to dash-cut through enemies. */
public class KatanaItem extends Item implements FrontierWeapon {
    public KatanaItem(ToolMaterial tier, int damage, float speed, Properties properties) {
        super(properties.sword(tier, damage, speed));
    }

    @Override
    public Kind kind() {
        return Kind.KATANA;
    }

    @Override
    public float critBonus() {
        return 0.15f;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        player.startUsingItem(hand);
        if (player instanceof ServerPlayer sp) ModNetwork.animate(sp, PlayerAnims.DRAW, 1200);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int charged = getUseDuration(stack, entity) - timeLeft;
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) return false;
        if (charged < 10) {
            ModNetwork.animate(player, PlayerAnims.SLASH_A, 1);
            return false;
        }
        Vec3 dir = player.getLookAngle().multiply(1, 0, 1).normalize();
        double range = Math.min(7, 3 + charged / 8.0);
        float damage = SkillExecutor.attack(player) * 1.6f;
        for (LivingEntity target : SkillExecutor.entitiesInLine(player, dir, range)) {
            SkillExecutor.strike(player, target, damage, true);
        }
        SkillExecutor.push(player, dir.x * range * 0.35, 0.05, dir.z * range * 0.35);
        Vec3 start = player.position().add(0, 1, 0);
        for (int i = 0; i < 20; i++) {
            Vec3 p = start.add(dir.scale(range * i / 20));
            server.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), com.slfrontier.registry.ModSounds.COMBAT_HEAVY_SLASH, SoundSource.PLAYERS, 1.2f, 1.25f);
        // The draw releases a flying crescent of wind (like Wezaemon's Tachikaze).
        com.slfrontier.fx.Fx.arc(player, 2.2f, 8, 170, 0xFFBFE8FF, 9);
        com.slfrontier.fx.Fx.wave(player, 1.8f, 5, (float) range, 0xFF8FD8FF, 12);
        ModNetwork.animate(player, PlayerAnims.IAI, 11);
        player.getCooldowns().addCooldown(stack, 30);
        stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.slfrontier.katana").withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.translatable("lore.slfrontier.tombguard_katana").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
