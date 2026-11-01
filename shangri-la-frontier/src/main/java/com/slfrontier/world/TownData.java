package com.slfrontier.world;

import com.slfrontier.SLFrontier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers which landmarks (towns, the Serpent Bridge...) players have discovered. Saves from
 * before the designed map also hold planned/built positions in the old Frontier; those are
 * dropped when such a save is loaded ({@link #VERSION}).
 */
public class TownData extends SavedData {
    private static final SavedDataType<TownData> TYPE = new SavedDataType<>(SLFrontier.id("towns"), TownData::new,
            CompoundTag.CODEC.xmap(TownData::load, TownData::save));
    /** 1: the Frontier became a designed map in a new dimension. */
    private static final int VERSION = 1;

    /** Where each site will go (chosen from terrain noise before anything is built). */
    private final Map<String, BlockPos> planned = new HashMap<>();
    /** Spawn/centre point of each site that has been built. */
    private final Map<String, BlockPos> built = new HashMap<>();
    /** Marker positions inside built sites, keyed "site.marker". */
    private final Map<String, BlockPos> markers = new HashMap<>();

    public static TownData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    private static TownData load(CompoundTag tag) {
        TownData data = new TownData();
        if (tag.getIntOr("Version", 0) < VERSION) {
            data.setDirty();
            return data;
        }
        // Worlds from before landmarks existed only stored the two towns.
        tag.read("Fastera", BlockPos.CODEC).ifPresent(p -> data.built.put("fastera", p));
        tag.read("Rabituza", BlockPos.CODEC).ifPresent(p -> data.built.put("rabituza", p));
        readMap(tag.getCompoundOrEmpty("Planned"), data.planned);
        readMap(tag.getCompoundOrEmpty("Built"), data.built);
        readMap(tag.getCompoundOrEmpty("Markers"), data.markers);
        return data;
    }

    private static void readMap(CompoundTag tag, Map<String, BlockPos> into) {
        for (String key : tag.keySet()) {
            into.put(key, BlockPos.of(tag.getLongOr(key, 0L)));
        }
    }

    private static CompoundTag writeMap(Map<String, BlockPos> map) {
        CompoundTag tag = new CompoundTag();
        map.forEach((k, v) -> tag.putLong(k, v.asLong()));
        return tag;
    }

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.put("Planned", writeMap(planned));
        tag.put("Built", writeMap(built));
        tag.put("Markers", writeMap(markers));
        return tag;
    }

    @Nullable
    public BlockPos built(String site) {
        return built.get(site);
    }

    @Nullable
    public BlockPos planned(String site) {
        return planned.get(site);
    }

    @Nullable
    public BlockPos marker(String site, String marker) {
        return markers.get(site + "." + marker);
    }

    public void setPlanned(String site, BlockPos pos) {
        planned.put(site, pos);
        setDirty();
    }

    public void setBuilt(String site, BlockPos pos) {
        built.put(site, pos);
        setDirty();
    }

    public void setMarker(String site, String marker, BlockPos pos) {
        markers.put(site + "." + marker, pos);
        setDirty();
    }

    // Shorthands for the two towns most of the code cares about.

    @Nullable
    public BlockPos fastera() {
        return built.get("fastera");
    }

    @Nullable
    public BlockPos rabituza() {
        return built.get("rabituza");
    }
}
