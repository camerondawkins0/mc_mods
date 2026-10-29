package com.slfrontier.client;

import com.slfrontier.SLFrontier;
import com.slfrontier.client.screen.FrontierInventoryScreen;
import com.slfrontier.network.InventoryNetwork;
import com.slfrontier.registry.ModMenus;
import com.slfrontier.world.Frontier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client glue for the Frontier inventory: inside the Frontier (outside creative) the vanilla
 * inventory screen is swapped for a request to the server, which opens the Frontier menu.
 */
@EventBusSubscriber(modid = SLFrontier.MODID, value = Dist.CLIENT)
public final class InventoryClient {
    private InventoryClient() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || next == null || next.getClass() != InventoryScreen.class || event.getCurrentScreen() != null) return;
        if (!Frontier.isIn(player) || player.isCreative() || player.isSpectator()) return;
        event.setCanceled(true);
        PacketDistributor.sendToServer(new InventoryNetwork.OpenInventory());
    }

    /** The avatar menu's Items tab: the Frontier inventory, or the creative inventory in creative mode. */
    public static void openInventory() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.player.isCreative()) {
            mc.setScreen(new InventoryScreen(mc.player));
        } else {
            PacketDistributor.sendToServer(new InventoryNetwork.OpenInventory());
        }
    }

    @EventBusSubscriber(modid = SLFrontier.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBusEvents {
        private ModBusEvents() {
        }

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenus.FRONTIER_INVENTORY.get(), FrontierInventoryScreen::new);
        }
    }
}
