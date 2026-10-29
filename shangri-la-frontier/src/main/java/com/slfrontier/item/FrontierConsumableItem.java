package com.slfrontier.item;

import com.slfrontier.data.PlayerData;
import com.slfrontier.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A crafted Frontier consumable: potions, tonics, bandages and rations. Each one heals,
 * restores Frontier stamina, grants effects and/or cures effects, as set by its {@link Spec}.
 * Items with a food component are eaten the vanilla way and apply their extras on top.
 */
public class FrontierConsumableItem extends TooltipItem {
    private final Spec spec;

    public FrontierConsumableItem(Properties properties, Spec spec) {
        super(properties);
        this.spec = spec;
    }

    /** What using the item does. Build with {@link #drink()} or {@link #apply(int)}. */
    public static final class Spec {
        private final int ticks;
        private final UseAnim anim;
        private float heal;
        private float stamina;
        private boolean fullStamina;
        private boolean bottle;
        @Nullable
        private SoundEvent sound;
        private final List<MobEffectInstance> effects = new ArrayList<>();
        private final List<Holder<MobEffect>> cures = new ArrayList<>();

        private Spec(int ticks, UseAnim anim) {
            this.ticks = ticks;
            this.anim = anim;
        }

        /** Health restored at once, in half-hearts. */
        public Spec heal(float amount) {
            this.heal = amount;
            return this;
        }

        public Spec stamina(float amount) {
            this.stamina = amount;
            return this;
        }

        public Spec fullStamina() {
            this.fullStamina = true;
            return this;
        }

        public Spec effect(Holder<MobEffect> effect, int ticks, int amplifier) {
            effects.add(new MobEffectInstance(effect, ticks, amplifier));
            return this;
        }

        public Spec cures(Holder<MobEffect> effect) {
            cures.add(effect);
            return this;
        }

        /** Leaves an empty glass bottle behind. */
        public Spec bottle() {
            this.bottle = true;
            return this;
        }

        public Spec sound(SoundEvent sound) {
            this.sound = sound;
            return this;
        }
    }

    public static Spec drink() {
        return new Spec(24, UseAnim.DRINK).bottle();
    }

    /** Something applied by hand (a bandage); also used for food, where the timing comes from the food component. */
    public static Spec apply(int ticks) {
        return new Spec(ticks, UseAnim.NONE);
    }

    private boolean isFood(ItemStack stack) {
        return stack.has(DataComponents.FOOD);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (isFood(player.getItemInHand(hand))) return super.use(level, player, hand);
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return isFood(stack) ? super.getUseDuration(stack, entity) : spec.ticks;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return isFood(stack) ? super.getUseAnimation(stack) : spec.anim;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        applyTo(level, entity);
        if (isFood(stack)) return super.finishUsingItem(stack, level, entity);
        stack.consume(1, entity);
        if (spec.bottle && !(entity instanceof Player p && p.getAbilities().instabuild)) {
            if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            if (entity instanceof Player p && !p.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
                p.drop(new ItemStack(Items.GLASS_BOTTLE), false);
            }
        }
        return stack;
    }

    private void applyTo(Level level, LivingEntity entity) {
        if (level.isClientSide) return;
        if (spec.heal > 0) entity.heal(spec.heal);
        for (Holder<MobEffect> cure : spec.cures) entity.removeEffect(cure);
        for (MobEffectInstance e : spec.effects) entity.addEffect(new MobEffectInstance(e));
        if (entity instanceof Player player && (spec.fullStamina || spec.stamina > 0)) {
            PlayerData data = ModAttachments.get(player);
            data.setStamina(spec.fullStamina ? data.maxStamina() : data.stamina() + spec.stamina);
        }
        if (spec.sound != null) level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), spec.sound, SoundSource.PLAYERS, 0.8f, 1f);
        if (spec.heal > 0 && level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + entity.getBbHeight() * 0.8, entity.getZ(),
                    Math.max(1, (int) (spec.heal / 4)), 0.3, 0.2, 0.3, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (spec.heal > 0) {
            float hearts = spec.heal / 2f;
            String n = hearts == (int) hearts ? String.valueOf((int) hearts) : String.valueOf(hearts);
            tooltip.add(Component.translatable("tooltip.slfrontier.heals", n).withStyle(ChatFormatting.RED));
        }
        if (spec.fullStamina) {
            tooltip.add(Component.translatable("tooltip.slfrontier.stamina_full").withStyle(ChatFormatting.GREEN));
        } else if (spec.stamina > 0) {
            tooltip.add(Component.translatable("tooltip.slfrontier.stamina", (int) spec.stamina).withStyle(ChatFormatting.GREEN));
        }
        for (Holder<MobEffect> cure : spec.cures) {
            tooltip.add(Component.translatable("tooltip.slfrontier.cures", cure.value().getDisplayName()).withStyle(ChatFormatting.AQUA));
        }
        for (MobEffectInstance e : spec.effects) {
            MutableComponent line = Component.translatable(e.getDescriptionId());
            if (e.getAmplifier() > 0) {
                line = Component.translatable("potion.withAmplifier", line, Component.translatable("potion.potency." + e.getAmplifier()));
            }
            line = Component.translatable("potion.withDuration", line, MobEffectUtil.formatDuration(e, 1f, context.tickRate()));
            tooltip.add(line.withStyle(e.getEffect().value().getCategory().getTooltipFormatting()));
        }
    }
}
