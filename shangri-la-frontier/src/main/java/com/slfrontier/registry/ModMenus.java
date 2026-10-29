package com.slfrontier.registry;

import com.slfrontier.SLFrontier;
import com.slfrontier.inventory.FrontierInventoryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SLFrontier.MODID);

    /** The RPG inventory that replaces the vanilla one inside the Frontier. */
    public static final DeferredHolder<MenuType<?>, MenuType<FrontierInventoryMenu>> FRONTIER_INVENTORY = MENUS.register(
            "frontier_inventory", () -> new MenuType<>(FrontierInventoryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {
    }
}
