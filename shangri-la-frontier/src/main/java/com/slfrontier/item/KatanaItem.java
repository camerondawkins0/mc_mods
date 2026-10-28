package com.slfrontier.item;

import com.slfrontier.network.ModNetwork;
import com.slfrontier.skill.PlayerAnims;
import com.slfrontier.skill.SkillExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** A katana. Hold use to draw into an iaijutsu stance, release to dash-cut through enemies. */
public class KatanaItem extends SwordItem implements FrontierWeapon {
    public KatanaItem(Tier tier, int damage, float speed, Properties properties) {
        super(tier, properties.attributes(SwordItem.createAttributes(tier, damage, speed)));
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(player.getItemInHand(hand));
        player.startUsingItem(hand);
        if (player instanceof ServerPlayer sp) ModNetwork.animate(sp, PlayerAnims.DRAW, 1200);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int charged = getUseDuration(stack, entity) - timeLeft;
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) return;
        if (charged < 10) {
            ModNetwork.animate(player, PlayerAnims.SLASH_A, 1);
            return;
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
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 1.6f);
        // The draw releases a flying crescent of wind (like Wezaemon's Tachikaze).
        com.slfrontier.fx.Fx.arc(player, 2.2f, 8, 170, 0xFFBFE8FF, 9);
        com.slfrontier.fx.Fx.wave(player, 1.8f, 5, (float) range, 0xFF8FD8FF, 12);
        ModNetwork.animate(player, PlayerAnims.IAI, 11);
        player.getCooldowns().addCooldown(this, 30);
        stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.slfrontier.katana").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("lore.slfrontier.tombguard_katana").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
