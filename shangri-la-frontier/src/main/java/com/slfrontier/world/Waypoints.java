package com.slfrontier.world;

import com.slfrontier.SLFrontier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every fast-travel waypoint in the Frontier: the towns, plus each Waypoint Crystal the
 * first time somebody attunes to it. Which ones a player may use is stored per player
 * ({@code PlayerData#waypoints}).
 */
public class Waypoints extends SavedData {
    private static final String NAME = "slfrontier_waypoints";

    public enum Kind { TOWN, CRYSTAL }

    public record Waypoint(String id, String nameKey, BlockPos pos, Kind kind) {
    }

    private final Map<String, Waypoint> all = new LinkedHashMap<>();

    public static Waypoints get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Waypoints::new, Waypoints::load, null), NAME);
    }

    public static String crystalId(BlockPos pos) {
        return "wp_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    public void put(Waypoint wp) {
        Waypoint old = all.get(wp.id());
        if (!wp.equals(old)) {
            all.put(wp.id(), wp);
            setDirty();
        }
    }

    public void remove(String id) {
        if (all.remove(id) != null) setDirty();
    }

    @Nullable
    public Waypoint byId(String id) {
        return all.get(id);
    }

    public Collection<Waypoint> all() {
        return all.values();
    }

    private static Waypoints load(CompoundTag tag, HolderLookup.Provider provider) {
        Waypoints data = new Waypoints();
        for (Tag t : tag.getList("Waypoints", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            BlockPos pos = NbtUtils.readBlockPos(c, "Pos").orElse(null);
            if (pos == null) continue;
            Kind kind;
            try {
                kind = Kind.valueOf(c.getString("Kind"));
            } catch (IllegalArgumentException e) {
                kind = Kind.CRYSTAL;
            }
            data.all.put(c.getString("Id"), new Waypoint(c.getString("Id"), c.getString("Name"), pos, kind));
        }
        SLFrontier.LOGGER.debug("Loaded {} waypoints", data.all.size());
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Waypoint wp : all.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("Id", wp.id());
            c.putString("Name", wp.nameKey());
            c.put("Pos", NbtUtils.writeBlockPos(wp.pos()));
            c.putString("Kind", wp.kind().name());
            list.add(c);
        }
        tag.put("Waypoints", list);
        return tag;
    }
}
