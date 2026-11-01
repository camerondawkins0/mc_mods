package com.slfrontier.item;

import com.slfrontier.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Moonblades Vysache forged from the Vorpal Choppers: "Waxing" (white) and
 * "Waning" (black). Waxing is stronger by day, Waning by night. Sneak-use while
 * holding one in each hand to join them into the Twin Moon.
 */
public class MoonbladeItem extends TwinBladeItem {
    private final boolean waxing;

    public MoonbladeItem(ToolMaterial tier, int damage, float speed, boolean waxing, Properties properties) {
        super(tier, damage, speed, 0.1f, waxing ? "lore.slfrontier.waxing" : "lore.slfrontier.waning", properties);
        this.waxing = waxing;
    }

    public boolean isWaxing() {
        return waxing;
    }

    /** Waxing shines during the day and Waning at night. */
    public float timeBonus(Level level) {
        boolean night = level.isDarkOutside();
        return waxing != night ? 1.2f : 1.0f;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (!(main.getItem() instanceof MoonbladeItem a) || !(off.getItem() instanceof MoonbladeItem b) || a.waxing == b.waxing) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.slfrontier.twin_moon.need_both").withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            ItemStack waxingStack = a.waxing ? main : off;
            ItemStack waningStack = a.waxing ? off : main;
            ItemStack twin = new ItemStack(ModItems.TWIN_MOON.get());
            CompoundTag tag = new CompoundTag();
            var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            tag.store("waxing", ItemStack.CODEC, ops, waxingStack);
            tag.store("waning", ItemStack.CODEC, ops, waningStack);
            twin.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            player.setItemInHand(InteractionHand.MAIN_HAND, twin);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.8f);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.6, 0.5, 0.05);
            }
            player.sendOverlayMessage(Component.translatable("message.slfrontier.twin_moon.joined").withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable(waxing ? "tooltip.slfrontier.waxing" : "tooltip.slfrontier.waning").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("tooltip.slfrontier.twin_moon_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
