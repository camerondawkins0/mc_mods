package com.slfrontier.client;

import com.slfrontier.network.ModNetwork;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client copies of server-side state that isn't part of the player data (map markers). */
public final class ClientState {
    /** A town and a waypoint crystal with the same name this close together are one place (Firstia and its crystal). */
    private static final int SAME_PLACE = 160;

    private static List<ModNetwork.Marker> markers = List.of();
    private static List<ModNetwork.Marker> waypoints = List.of();
    @Nullable
    private static ModNetwork.Marker quest;
    /** Waypoint crystals folded into the town they stand in: town id to crystal id. */
    private static Map<String, String> folded = Map.of();
    private static boolean fastTravel;

    private ClientState() {
    }

    private static boolean sameName(ModNetwork.Marker a, ModNetwork.Marker b) {
        return a.nameKey().equals(b.nameKey())
                || net.minecraft.client.resources.language.I18n.get(a.nameKey()).equals(net.minecraft.client.resources.language.I18n.get(b.nameKey()));
    }

    public static void setMarkers(List<ModNetwork.Marker> list, boolean fastTravelEnabled) {
        markers = List.copyOf(list);
        fastTravel = fastTravelEnabled;
        quest = null;
        for (ModNetwork.Marker m : markers) {
            if (m.kind() == ModNetwork.Marker.QUEST) {
                quest = m;
                break;
            }
        }
        // Waypoints and towns, towns first; a crystal inside a town of the same name is listed once, as the town.
        List<ModNetwork.Marker> out = new ArrayList<>();
        Map<String, String> merged = new HashMap<>();
        for (ModNetwork.Marker m : markers) if (m.kind() == ModNetwork.Marker.TOWN) out.add(m);
        for (ModNetwork.Marker m : markers) {
            if (m.kind() != ModNetwork.Marker.WAYPOINT) continue;
            ModNetwork.Marker town = null;
            for (ModNetwork.Marker t : markers) {
                // Compare what the player reads: a town and its crystal can use different keys for the same name.
                if (t.kind() == ModNetwork.Marker.TOWN && sameName(t, m)
                        && t.pos().distSqr(m.pos()) < SAME_PLACE * SAME_PLACE && !merged.containsKey(t.id())) {
                    town = t;
                    break;
                }
            }
            if (town != null) {
                merged.put(town.id(), m.id());
            } else {
                out.add(m);
            }
        }
        waypoints = List.copyOf(out);
        folded = Map.copyOf(merged);
    }

    public static List<ModNetwork.Marker> markers() {
        return markers;
    }

    public static boolean fastTravelEnabled() {
        return fastTravel;
    }

    @Nullable
    public static ModNetwork.Marker questMarker() {
        return quest;
    }

    /** Waypoints and towns, towns first, with a town's own crystal folded into it. Cached until the markers change. */
    public static List<ModNetwork.Marker> waypoints() {
        return waypoints;
    }

    /** Whether a listed waypoint is (or, for a town, holds the crystal that is) the given waypoint id. */
    public static boolean isWaypoint(ModNetwork.Marker listed, @Nullable String id) {
        return id != null && (id.equals(listed.id()) || id.equals(folded.get(listed.id())));
    }

    public static void reset() {
        markers = List.of();
        waypoints = List.of();
        quest = null;
        folded = Map.of();
        fastTravel = false;
    }
}
