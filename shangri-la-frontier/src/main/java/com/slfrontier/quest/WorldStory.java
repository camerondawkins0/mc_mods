package com.slfrontier.quest;

import com.mojang.serialization.Codec;
import com.slfrontier.SLFrontier;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Story facts that belong to the world rather than to one avatar: the one-off monsters that
 * live in a site's template (the Mud Digger in its lair, the Empire Bee Queen in her hive) and
 * never come back once slain. Chapters and side quests that need them check these flags, so
 * killing one early (or a friend killing it) never leaves a quest impossible.
 * Saved as {@code data/slfrontier/world_story.dat}.
 */
public class WorldStory extends SavedData {
    public static final String MUD_DIGGER_SLAIN = "mud_digger_slain";
    public static final String BEE_QUEEN_SLAIN = "bee_queen_slain";

    private static final Identifier ID = SLFrontier.id("world_story");
    private static final Codec<WorldStory> CODEC = Codec.STRING.listOf().optionalFieldOf("flags", List.of()).codec()
            .xmap(WorldStory::new, w -> List.copyOf(w.flags));
    private static final SavedDataType<WorldStory> TYPE = new SavedDataType<>(ID, WorldStory::new, CODEC);

    private final Set<String> flags = new HashSet<>();

    public WorldStory() {
    }

    private WorldStory(List<String> flags) {
        this.flags.addAll(flags);
    }

    public static WorldStory get(ServerLevel anyLevel) {
        return anyLevel.getServer().getDataStorage().computeIfAbsent(TYPE);
    }

    public static boolean has(ServerLevel anyLevel, String flag) {
        return get(anyLevel).flags.contains(flag);
    }

    public static void set(ServerLevel anyLevel, String flag) {
        WorldStory data = get(anyLevel);
        if (data.flags.add(flag)) data.setDirty();
    }
}
