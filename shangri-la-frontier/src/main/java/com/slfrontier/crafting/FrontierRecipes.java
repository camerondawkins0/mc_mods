package com.slfrontier.crafting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.slfrontier.SLFrontier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-side registry of Frontier recipes, reloaded with data packs ({@code /reload}). */
public final class FrontierRecipes extends SimpleJsonResourceReloadListener {
    public static final FrontierRecipes INSTANCE = new FrontierRecipes();
    private static final Gson GSON = new GsonBuilder().create();
    /** Display order everywhere: station, category, level, then id. */
    public static final Comparator<FrontierRecipe> ORDER = Comparator.comparingInt((FrontierRecipe r) -> r.station().ordinal())
            .thenComparingInt(FrontierRecipe::categoryIndex)
            .thenComparingInt(FrontierRecipe::minLevel)
            .thenComparing(r -> r.id().toString());

    private static volatile Map<ResourceLocation, FrontierRecipe> recipes = Map.of();

    private FrontierRecipes() {
        super(GSON, "frontier_recipe");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<FrontierRecipe> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            try {
                loaded.add(FrontierRecipe.fromJson(e.getKey(), GsonHelper.convertToJsonObject(e.getValue(), "recipe")));
            } catch (RuntimeException ex) {
                SLFrontier.LOGGER.error("Skipping Frontier recipe {}: {}", e.getKey(), ex.getMessage());
            }
        }
        loaded.sort(ORDER);
        Map<ResourceLocation, FrontierRecipe> map = new LinkedHashMap<>();
        for (FrontierRecipe r : loaded) map.put(r.id(), r);
        recipes = map;
        SLFrontier.LOGGER.info("Loaded {} Frontier recipes", map.size());
    }

    public static List<FrontierRecipe> all() {
        return List.copyOf(recipes.values());
    }

    @Nullable
    public static FrontierRecipe get(ResourceLocation id) {
        return recipes.get(id);
    }
}
