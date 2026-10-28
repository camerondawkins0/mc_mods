package com.slfrontier.client;

import com.slfrontier.network.ModNetwork;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Client copies of server-side state that isn't part of the player data (map markers). */
public final class ClientState {
    private static List<ModNetwork.Marker> markers = List.of();
    private static boolean fastTravel;

    private ClientState() {
    }

    public static void setMarkers(List<ModNetwork.Marker> list, boolean fastTravelEnabled) {
        markers = List.copyOf(list);
        fastTravel = fastTravelEnabled;
    }

    public static List<ModNetwork.Marker> markers() {
        return markers;
    }

    public static boolean fastTravelEnabled() {
        return fastTravel;
    }

    @Nullable
    public static ModNetwork.Marker questMarker() {
        for (ModNetwork.Marker m : markers) if (m.kind() == ModNetwork.Marker.QUEST) return m;
        return null;
    }

    /** Waypoints and towns, towns first. */
    public static List<ModNetwork.Marker> waypoints() {
        List<ModNetwork.Marker> out = new ArrayList<>();
        for (ModNetwork.Marker m : markers) if (m.kind() == ModNetwork.Marker.TOWN) out.add(m);
        for (ModNetwork.Marker m : markers) if (m.kind() == ModNetwork.Marker.WAYPOINT) out.add(m);
        return out;
    }

    public static void reset() {
        markers = List.of();
        fastTravel = false;
    }
}
