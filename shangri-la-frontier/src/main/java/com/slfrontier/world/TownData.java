package com.slfrontier.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** Remembers where the hand-placed towns (Fastera and Rabituza) were built. */
public class TownData extends SavedData {
    private static final String NAME = "slfrontier_towns";

    @Nullable
    private BlockPos fastera;
    @Nullable
    private BlockPos rabituza;

    public static TownData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TownData::new, TownData::load, null), NAME);
    }

    private static TownData load(CompoundTag tag, HolderLookup.Provider provider) {
        TownData data = new TownData();
        NbtUtils.readBlockPos(tag, "Fastera").ifPresent(p -> data.fastera = p);
        NbtUtils.readBlockPos(tag, "Rabituza").ifPresent(p -> data.rabituza = p);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (fastera != null) tag.put("Fastera", NbtUtils.writeBlockPos(fastera));
        if (rabituza != null) tag.put("Rabituza", NbtUtils.writeBlockPos(rabituza));
        return tag;
    }

    @Nullable
    public BlockPos fastera() {
        return fastera;
    }

    @Nullable
    public BlockPos rabituza() {
        return rabituza;
    }

    public void setFastera(BlockPos pos) {
        this.fastera = pos;
        setDirty();
    }

    public void setRabituza(BlockPos pos) {
        this.rabituza = pos;
        setDirty();
    }
}
