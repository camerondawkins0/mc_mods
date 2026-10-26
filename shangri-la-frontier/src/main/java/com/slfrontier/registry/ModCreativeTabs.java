package com.slfrontier.registry;

import com.slfrontier.SLFrontier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SLFrontier.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.slfrontier"))
            .icon(() -> new ItemStack(ModItems.FULLDIVE_HEADSET.get()))
            .displayItems((params, output) -> {
                for (var entry : ModItems.ITEMS.getEntries()) {
                    Item item = entry.get();
                    boolean egg = ModItems.SPAWN_EGGS.stream().anyMatch(e -> e.get() == item);
                    if (!egg && item != ModItems.TWIN_MOON.get()) output.accept(item);
                }
                for (var egg : ModItems.SPAWN_EGGS) output.accept(egg.get());
            })
            .build());

    private ModCreativeTabs() {
    }
}
