package com.slfrontier.quest;

import com.slfrontier.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Optional quests handed out by NPCs. Each has one objective; progress is stored on the
 * player ({@code PlayerData#sideProgress}) and rewards are paid when it is turned in to
 * the NPC who gave it.
 */
public enum SideQuest {
    GOBLIN_CULL("goblin_cull", "frontier_guide", Objective.KILL, "slfrontier:goblin", 12, 180, QuestStage.RAVENOUS_SNAKE,
            () -> new ItemStack(Items.GOLDEN_APPLE), () -> new ItemStack(ModItems.HOLY_WATER.get(), 2)),
    SOLO_KILLER("solo_killer", "frontier_blacksmith", Objective.COLLECT, "slfrontier:mud_digger_fin", 1, 300,
            QuestStage.NIGHTSLAYER_ENCOUNTER, () -> new ItemStack(ModItems.REBIRTH_TEARJEWEL.get())),
    BEETLE_HUNTER("beetle_hunter", "bilac", Objective.COLLECT, "slfrontier:quad_beetle_shell", 3, 400, QuestStage.MOONBLADES,
            () -> new ItemStack(ModItems.REALLOCATION_TONIC.get())),
    QUEENS_CROWN("queens_crown", "bilac", Objective.KILL, "slfrontier:empire_bee_queen", 1, 600, QuestStage.MOONBLADES,
            () -> new ItemStack(ModItems.EMPIRE_BEE_STINGER.get(), 4)),
    SHADOW_PELTS("shadow_pelts", "vysache", Objective.COLLECT, "slfrontier:nightslayer_fur", 6, 500, QuestStage.FIND_SETSUNA,
            () -> new ItemStack(ModItems.REBIRTH_TEARJEWEL.get())),
    LILY_OFFERING("lily_offering", "setsuna", Objective.COLLECT, "slfrontier:red_spider_lily", 8, 250, QuestStage.NIGHTSLAYER_FINAL,
            () -> new ItemStack(ModItems.HOLY_WATER.get(), 4)),
    SEASONED_ADVENTURER("seasoned_adventurer", "frontier_guide", Objective.LEVEL, "", 20, 0, QuestStage.GRAY_ORE,
            () -> new ItemStack(ModItems.REBIRTH_TEARJEWEL.get()), () -> new ItemStack(Items.DIAMOND, 3)),
    REDCAP_RAID("redcap_raid", "frontier_blacksmith", Objective.KILL, "slfrontier:redcap_goblin", 5, 300, QuestStage.GRAY_ORE,
            () -> new ItemStack(ModItems.GRAY_ORE_CHUNK.get(), 8), () -> new ItemStack(ModItems.HOLY_WATER.get(), 2)),
    GIANT_KILLER("giant_killer", "pencilgon", Objective.KILL, "slfrontier:quad_beetle", 3, 600, QuestStage.THIRDREMA,
            () -> new ItemStack(ModItems.REALLOCATION_TONIC.get()), () -> new ItemStack(ModItems.REBIRTH_TEARJEWEL.get())),
    SPIRIT_FIST_SPARRING("spirit_fist_sparring", "oikatzo", Objective.KILL, "slfrontier:redcap_goblin", 10, 500, QuestStage.THIRDREMA,
            () -> new ItemStack(Items.GOLDEN_APPLE, 2), () -> new ItemStack(ModItems.HOLY_WATER.get(), 3));

    public enum Objective { KILL, COLLECT, LEVEL, VISIT }

    public final String id;
    public final String giver;
    public final Objective objective;
    /** Entity type id (KILL), item id (COLLECT), biome/structure id (VISIT); unused for LEVEL. */
    public final String target;
    public final int goal;
    public final int exp;
    public final QuestStage unlockedAt;
    private final List<Supplier<ItemStack>> rewards;

    @SafeVarargs
    SideQuest(String id, String giver, Objective objective, String target, int goal, int exp, QuestStage unlockedAt,
              Supplier<ItemStack>... rewards) {
        this.id = id;
        this.giver = giver;
        this.objective = objective;
        this.target = target;
        this.goal = goal;
        this.exp = exp;
        this.unlockedAt = unlockedAt;
        this.rewards = List.of(rewards);
    }

    public List<ItemStack> rewards() {
        return rewards.stream().map(Supplier::get).toList();
    }

    public String titleKey() {
        return "sidequest.slfrontier." + id + ".title";
    }

    public String descKey() {
        return "sidequest.slfrontier." + id + ".desc";
    }

    public String objectiveKey() {
        return "sidequest.slfrontier." + id + ".objective";
    }

    @Nullable
    public static SideQuest byId(String id) {
        for (SideQuest q : values()) {
            if (q.id.equals(id)) return q;
        }
        return null;
    }
}
