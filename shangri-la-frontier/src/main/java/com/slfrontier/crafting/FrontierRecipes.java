package com.slfrontier.crafting;

import com.google.gson.JsonElement;
import com.slfrontier.SLFrontier;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ExtraCodecs;
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
public final class FrontierRecipes extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final FrontierRecipes INSTANCE = new FrontierRecipes();
    /** Display order everywhere: station, category, level, then id. */
    public static final Comparator<FrontierRecipe> ORDER = Comparator.comparingInt((FrontierRecipe r) -> r.station().ordinal())
            .thenComparingInt(FrontierRecipe::categoryIndex)
            .thenComparingInt(FrontierRecipe::minLevel)
            .thenComparing(r -> r.id().toString());

    private static volatile Map<Identifier, FrontierRecipe> recipes = Map.of();

    private FrontierRecipes() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("frontier_recipe"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<FrontierRecipe> loaded = new ArrayList<>();
        for (Map.Entry<Identifier, JsonElement> e : files.entrySet()) {
            try {
                loaded.add(FrontierRecipe.fromJson(e.getKey(), GsonHelper.convertToJsonObject(e.getValue(), "recipe")));
            } catch (RuntimeException ex) {
                SLFrontier.LOGGER.error("Skipping Frontier recipe {}: {}", e.getKey(), ex.getMessage());
            }
        }
        loaded.sort(ORDER);
        Map<Identifier, FrontierRecipe> map = new LinkedHashMap<>();
        for (FrontierRecipe r : loaded) map.put(r.id(), r);
        recipes = map;
        SLFrontier.LOGGER.info("Loaded {} Frontier recipes", map.size());
    }

    public static List<FrontierRecipe> all() {
        return List.copyOf(recipes.values());
    }

    @Nullable
    public static FrontierRecipe get(Identifier id) {
        return recipes.get(id);
    }
}
