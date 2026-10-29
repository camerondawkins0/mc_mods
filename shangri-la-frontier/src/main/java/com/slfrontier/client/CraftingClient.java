package com.slfrontier.client;

import com.slfrontier.SLFrontier;
import com.slfrontier.client.screen.CraftingScreen;
import com.slfrontier.crafting.FrontierRecipe;
import com.slfrontier.crafting.FrontierRecipes;
import com.slfrontier.crafting.Station;
import com.slfrontier.network.CraftingNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client copy of the Frontier recipes and entry points for crafting packets. Only ever
 * called on the physical client.
 */
@EventBusSubscriber(modid = SLFrontier.MODID, value = Dist.CLIENT)
public final class CraftingClient {
    private static List<FrontierRecipe> recipes = List.of();
    private static Map<ResourceLocation, FrontierRecipe> byId = Map.of();

    private CraftingClient() {
    }

    public static void setRecipes(List<FrontierRecipe> list) {
        List<FrontierRecipe> sorted = new ArrayList<>(list);
        sorted.sort(FrontierRecipes.ORDER);
        Map<ResourceLocation, FrontierRecipe> map = new LinkedHashMap<>();
        for (FrontierRecipe r : sorted) map.put(r.id(), r);
        recipes = List.copyOf(sorted);
        byId = map;
        if (Minecraft.getInstance().screen instanceof CraftingScreen screen) screen.recipesChanged();
    }

    public static List<FrontierRecipe> recipes() {
        return recipes;
    }

    @Nullable
    public static FrontierRecipe get(ResourceLocation id) {
        return byId.get(id);
    }

    public static void open(CraftingNetwork.OpenCrafting payload) {
        Minecraft.getInstance().setScreen(new CraftingScreen(Station.byIndex(payload.station()), payload.npcEntity(), payload.npcId(),
                payload.pos()));
    }

    public static void status(CraftingNetwork.CraftStatus payload) {
        if (Minecraft.getInstance().screen instanceof CraftingScreen screen) screen.onStatus(payload);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        recipes = List.of();
        byId = Map.of();
    }
}
