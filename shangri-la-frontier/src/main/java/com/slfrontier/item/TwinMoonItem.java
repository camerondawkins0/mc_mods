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
    /** Custom-data key: the Twin Moon's wear (0..1) when the blades were joined. */
    static final String JOINED_WEAR = "joined_wear";

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

    /** Waxing and Waning shine together: +10% damage by day and by night. */
    public float damageBonus() {
        return 1.1f;
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
        ItemStack waxing = storedBlade(tag, "waxing", ops, ModItems.MOONBLADE_WAXING.get());
        ItemStack waning = storedBlade(tag, "waning", ops, ModItems.MOONBLADE_WANING.get());
        // The wear the Twin Moon gathered since it was joined goes to both blades (no free repair by
        // splitting and joining again).
        float gathered = Math.max(0f, MoonbladeItem.wear(stack) - tag.getFloatOr(JOINED_WEAR, 0f));
        for (ItemStack blade : new ItemStack[]{waxing, waning}) {
            if (!blade.isDamageableItem()) continue;
            blade.setDamageValue(Math.min(blade.getMaxDamage() - 1, blade.getDamageValue() + Math.round(gathered * blade.getMaxDamage())));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, waxing);
        player.setItemInHand(InteractionHand.OFF_HAND, waning);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 1.2f);
        player.sendOverlayMessage(Component.translatable("message.slfrontier.twin_moon.split").withStyle(ChatFormatting.AQUA));
    }

    /** One of the two blades stored inside; a fresh one (and a warning in the log) if it can't be read. */
    private static ItemStack storedBlade(CompoundTag tag, String key, com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops, Item fallback) {
        ItemStack blade = tag.read(key, ItemStack.CODEC, ops).orElse(ItemStack.EMPTY);
        if (blade.isEmpty() || !blade.is(fallback)) {
            com.slfrontier.SLFrontier.LOGGER.warn("Twin Moon held no readable {} blade ({}); handing out a new one", key, tag.get(key));
            return new ItemStack(fallback);
        }
        return blade;
    }

    /** The Twin Moon holds both one-of-a-kind Moonblades: it wears down to its last point but never shatters. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @org.jetbrains.annotations.Nullable T entity,
                                                   Consumer<Item> onBroken) {
        return MoonbladeItem.neverBreak(stack, amount);
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
        level.playSound(null, player.getX(), player.getY(), player.getZ(), com.slfrontier.registry.ModSounds.COMBAT_HEAVY_SLASH, SoundSource.PLAYERS, 1.3f, 0.85f);
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
