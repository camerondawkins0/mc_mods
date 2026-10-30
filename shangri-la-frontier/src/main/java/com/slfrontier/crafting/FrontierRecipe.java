package com.slfrontier.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.slfrontier.data.PlayerData;
import com.slfrontier.quest.QuestStage;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A Frontier crafting recipe, loaded from {@code data/<ns>/frontier_recipe/*.json} and mirrored
 * to clients. Unlike vanilla recipes there is no grid: a recipe is a list of ingredients with
 * counts, made at one {@link Station}, optionally only by one NPC smith, and gated by avatar
 * level and story progress.
 *
 * @param npc   entity id path of the only NPC who can make it (e.g. "bilac"), or "" for anyone
 * @param time  crafting time in ticks for one craft
 */
public record FrontierRecipe(ResourceLocation id, Station station, String category, ItemStack result, List<Part> ingredients,
                             int minLevel, @Nullable QuestStage requiresStage, String npc, int time) {
    /** Category order for tabs and sorting; recipes may use other names, which sort last. */
    public static final List<String> CATEGORIES = List.of("weapon", "armor", "consumable", "material", "gear");

    /** Why a recipe can't be made right now, most fundamental first. */
    public enum Lock { NONE, STATION, NPC, STAGE, LEVEL }

    /** One ingredient: an item or an item tag, times a count. */
    public record Part(@Nullable Item item, @Nullable TagKey<Item> tag, int count) {
        public boolean test(ItemStack stack) {
            if (stack.isEmpty()) return false;
            return item != null ? stack.is(item) : tag != null && stack.is(tag);
        }

        /** Items that satisfy this part, for icons. Tags resolve against the current (synced) tag set. */
        public List<ItemStack> examples() {
            if (item != null) return List.of(new ItemStack(item));
            List<ItemStack> out = new ArrayList<>();
            if (tag != null) {
                BuiltInRegistries.ITEM.getTag(tag).ifPresent(set -> {
                    for (Holder<Item> h : set) out.add(new ItemStack(h));
                });
            }
            if (out.isEmpty()) out.add(new ItemStack(Items.BARRIER));
            return out;
        }

        public Component name() {
            if (item != null) return item.getDescription();
            return tag == null ? Component.empty() : Component.translatable(Tags.getTagTranslationKey(tag));
        }

        private static final StreamCodec<RegistryFriendlyByteBuf, Part> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBoolean(p.tag != null);
            buf.writeResourceLocation(p.tag != null ? p.tag.location() : BuiltInRegistries.ITEM.getKey(p.item));
            buf.writeVarInt(p.count);
        }, buf -> {
            boolean isTag = buf.readBoolean();
            ResourceLocation rl = buf.readResourceLocation();
            int count = buf.readVarInt();
            return isTag ? new Part(null, TagKey.create(Registries.ITEM, rl), count) : new Part(BuiltInRegistries.ITEM.get(rl), null, count);
        });
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, FrontierRecipe> STREAM_CODEC = StreamCodec.of((buf, r) -> {
        buf.writeResourceLocation(r.id);
        buf.writeVarInt(r.station.ordinal());
        buf.writeUtf(r.category);
        ItemStack.STREAM_CODEC.encode(buf, r.result);
        Part.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, r.ingredients);
        buf.writeVarInt(r.minLevel);
        buf.writeUtf(r.requiresStage == null ? "" : r.requiresStage.name());
        buf.writeUtf(r.npc);
        buf.writeVarInt(r.time);
    }, buf -> new FrontierRecipe(buf.readResourceLocation(), Station.byIndex(buf.readVarInt()), buf.readUtf(),
            ItemStack.STREAM_CODEC.decode(buf), Part.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readVarInt(),
            QuestStage.byName(buf.readUtf()), buf.readUtf(), buf.readVarInt()));

    // ---- Loading -------------------------------------------------------------------

    /** Parses one recipe file. The file location is the id unless the JSON names one. */
    public static FrontierRecipe fromJson(ResourceLocation fileId, JsonObject json) {
        ResourceLocation id = json.has("id") ? ResourceLocation.parse(GsonHelper.getAsString(json, "id")) : fileId;
        Station station = Station.byId(GsonHelper.getAsString(json, "station"));
        if (station == null) throw new JsonParseException("unknown station " + json.get("station"));
        String category = GsonHelper.getAsString(json, "category", "gear").toLowerCase(Locale.ROOT);

        JsonObject res = GsonHelper.getAsJsonObject(json, "result");
        Item resultItem = item(GsonHelper.getAsString(res, "item"));
        ItemStack result = new ItemStack(resultItem, Math.max(1, GsonHelper.getAsInt(res, "count", 1)));

        List<Part> parts = new ArrayList<>();
        JsonArray arr = GsonHelper.getAsJsonArray(json, "ingredients");
        for (JsonElement el : arr) {
            JsonObject o = GsonHelper.convertToJsonObject(el, "ingredient");
            int count = Math.max(1, GsonHelper.getAsInt(o, "count", 1));
            if (o.has("tag")) {
                parts.add(new Part(null, TagKey.create(Registries.ITEM, ResourceLocation.parse(GsonHelper.getAsString(o, "tag"))), count));
            } else {
                parts.add(new Part(item(GsonHelper.getAsString(o, "item")), null, count));
            }
        }
        if (parts.isEmpty()) throw new JsonParseException("recipe has no ingredients");

        QuestStage stage = null;
        if (json.has("requires_stage")) {
            stage = QuestStage.byName(GsonHelper.getAsString(json, "requires_stage").toUpperCase(Locale.ROOT));
            if (stage == null) throw new JsonParseException("unknown quest stage " + json.get("requires_stage"));
        }
        return new FrontierRecipe(id, station, category, result, List.copyOf(parts), Math.max(1, GsonHelper.getAsInt(json, "min_level", 1)),
                stage, GsonHelper.getAsString(json, "npc", ""), Math.max(1, GsonHelper.getAsInt(json, "time", 40)));
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.parse(id);
        return BuiltInRegistries.ITEM.getOptional(rl).filter(i -> i != Items.AIR)
                .orElseThrow(() -> new JsonParseException("unknown item " + id));
    }

    /** Ticks for a batch: the first craft takes the full time, each extra one a third of it. Capped at 20 seconds. */
    public int batchTicks(int quantity) {
        return Math.min(20 * 20, time + (Math.max(1, quantity) - 1) * Math.max(4, time / 3));
    }

    /** Sort key: category, then level, then name. */
    public int categoryIndex() {
        int i = CATEGORIES.indexOf(category);
        return i < 0 ? CATEGORIES.size() : i;
    }

    // ---- Rules shared by client (display) and server (authority) -------------------------

    /** Checks everything except materials, for crafting at {@code at} with {@code npcId} ("" at a block). */
    public Lock lock(PlayerData data, Station at, String npcId) {
        if (station != at) return Lock.STATION;
        if (!npc.isEmpty() && !npc.equals(npcId)) return Lock.NPC;
        if (requiresStage != null && !data.stage().atLeast(requiresStage)) return Lock.STAGE;
        if (data.level() < minLevel) return Lock.LEVEL;
        return Lock.NONE;
    }

    /** Slots crafting may take from: the main inventory and the off hand, never worn armor. */
    private static List<ItemStack> sources(Player player) {
        Inventory inv = player.getInventory();
        List<ItemStack> out = new ArrayList<>(inv.items.size() + inv.offhand.size());
        out.addAll(inv.items);
        out.addAll(inv.offhand);
        return out;
    }

    /** How many of a part's items the player carries. */
    public static int have(Player player, Part part) {
        int n = 0;
        for (ItemStack stack : sources(player)) {
            if (part.test(stack)) n += stack.getCount();
        }
        return n;
    }

    /** How many times (up to {@code limit}) this recipe can be made from what the player carries. */
    public int maxCrafts(Player player, int limit) {
        List<ItemStack> src = sources(player);
        int upper = limit;
        for (Part p : ingredients) upper = Math.min(upper, have(player, p) / p.count());
        // Parts may overlap (an item matching two tags), so confirm by simulating the takes.
        for (int k = upper; k > 0; k--) {
            if (simulate(src, k)) return k;
        }
        return 0;
    }

    private boolean simulate(List<ItemStack> src, int times) {
        int[] left = new int[src.size()];
        for (int i = 0; i < left.length; i++) left[i] = src.get(i).getCount();
        for (Part p : ingredients) {
            int need = p.count() * times;
            for (int i = 0; i < left.length && need > 0; i++) {
                if (left[i] > 0 && p.test(src.get(i))) {
                    int take = Math.min(need, left[i]);
                    left[i] -= take;
                    need -= take;
                }
            }
            if (need > 0) return false;
        }
        return true;
    }

    /** Removes the ingredients for {@code times} crafts. Call only after {@link #maxCrafts} allowed it. */
    public void consume(Player player, int times) {
        List<ItemStack> src = sources(player);
        for (Part p : ingredients) {
            int need = p.count() * times;
            for (int i = 0; i < src.size() && need > 0; i++) {
                ItemStack stack = src.get(i);
                if (p.test(stack)) {
                    int take = Math.min(need, stack.getCount());
                    stack.shrink(take);
                    need -= take;
                }
            }
        }
        player.getInventory().setChanged();
    }
}
