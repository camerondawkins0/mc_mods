package com.slfrontier.event;

import com.slfrontier.SLFrontier;
import com.slfrontier.crafting.FrontierCrafting;
import com.slfrontier.crafting.FrontierRecipes;
import com.slfrontier.network.CraftingNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Game-bus hooks for Frontier crafting: loading and syncing recipes, running crafts, cleanup. */
@EventBusSubscriber(modid = SLFrontier.MODID)
public final class CraftingEvents {
    private CraftingEvents() {
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(FrontierRecipes.INSTANCE);
    }

    /** Fires for a joining player, and for everyone after {@code /reload}. */
    @SubscribeEvent
    public static void syncRecipes(OnDatapackSyncEvent event) {
        CraftingNetwork.SyncRecipes payload = new CraftingNetwork.SyncRecipes(FrontierRecipes.all());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        FrontierCrafting.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) FrontierCrafting.forget(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) FrontierCrafting.cancel(player);
    }
}
