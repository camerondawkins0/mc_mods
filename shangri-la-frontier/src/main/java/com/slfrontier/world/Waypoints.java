package com.slfrontier.world;

import com.slfrontier.SLFrontier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
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
    /** Saved as {@code data/slfrontier/waypoints.dat} in the world folder; see {@link LegacySaves}. */
    static final Identifier ID = SLFrontier.id("waypoints");
    private static final SavedDataType<Waypoints> TYPE = new SavedDataType<>(ID, Waypoints::new,
            CompoundTag.CODEC.xmap(Waypoints::load, Waypoints::save));

    public enum Kind { TOWN, CRYSTAL }

    public record Waypoint(String id, String nameKey, BlockPos pos, Kind kind) {
    }

    /** 1: the Frontier became a designed map in a new dimension. */
    private static final int VERSION = 1;
    private final Map<String, Waypoint> all = new LinkedHashMap<>();

    public static Waypoints get(ServerLevel anyLevel) {
        // World-wide, so it lives in the world's own data folder rather than a dimension's.
        return anyLevel.getServer().getDataStorage().computeIfAbsent(TYPE);
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

    private static Waypoints load(CompoundTag tag) {
        Waypoints data = new Waypoints();
        if (tag.getIntOr("Version", 0) < VERSION) {
            // Waypoints of the old Frontier point at places that no longer exist.
            data.setDirty();
            return data;
        }
        for (Tag t : tag.getListOrEmpty("Waypoints")) {
            CompoundTag c = (CompoundTag) t;
            BlockPos pos = c.read("Pos", BlockPos.CODEC).orElse(null);
            if (pos == null) continue;
            Kind kind;
            try {
                kind = Kind.valueOf(c.getStringOr("Kind", ""));
            } catch (IllegalArgumentException e) {
                kind = Kind.CRYSTAL;
            }
            data.all.put(c.getStringOr("Id", ""), new Waypoint(c.getStringOr("Id", ""), c.getStringOr("Name", ""), pos, kind));
        }
        SLFrontier.LOGGER.debug("Loaded {} waypoints", data.all.size());
        return data;
    }

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Waypoint wp : all.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("Id", wp.id());
            c.putString("Name", wp.nameKey());
            c.store("Pos", BlockPos.CODEC, wp.pos());
            c.putString("Kind", wp.kind().name());
            list.add(c);
        }
        tag.putInt("Version", VERSION);
        tag.put("Waypoints", list);
        return tag;
    }
}
