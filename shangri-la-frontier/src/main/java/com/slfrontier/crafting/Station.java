package com.slfrontier.crafting;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/** Where a Frontier recipe is made: the smiths' forge, the alchemy table or the camp workbench. */
public enum Station implements StringRepresentable {
    FORGE("forge", 0xFFFF9A3A),
    ALCHEMY("alchemy", 0xFFB57CFF),
    WORKBENCH("workbench", 0xFF7FE07F);

    public static final Codec<Station> CODEC = StringRepresentable.fromEnum(Station::values);

    public final String id;
    /** Accent colour for the crafting screen. */
    public final int color;

    Station(String id, int color) {
        this.id = id;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String nameKey() {
        return "station.slfrontier." + id;
    }

    /** GUI icon under textures/gui/icon/. */
    public String icon() {
        return "station_" + id;
    }

    @Nullable
    public static Station byId(String id) {
        for (Station s : values()) {
            if (s.id.equalsIgnoreCase(id)) return s;
        }
        return null;
    }

    public static Station byIndex(int index) {
        Station[] values = values();
        return values[Math.max(0, Math.min(index, values.length - 1))];
    }
}
