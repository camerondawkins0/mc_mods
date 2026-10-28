package com.slfrontier.world;

import com.slfrontier.SlfConfig;
import com.slfrontier.data.PlayerData;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.quest.Story;
import com.slfrontier.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps each player's map markers (quest objective, attuned waypoints, towns) in sync,
 * and discovers towns as players walk into them.
 */
public final class MapSync {
    /** Players discover a town's waypoint within this many blocks of its centre. */
    private static final int TOWN_RADIUS = 40;
    private static final Map<UUID, Integer> SENT = new ConcurrentHashMap<>();

    private MapSync() {
    }

    public static void invalidate(ServerPlayer player) {
        SENT.remove(player.getUUID());
    }

    public static void forget(ServerPlayer player) {
        SENT.remove(player.getUUID());
    }

    /** Runs once a second from the player tick. */
    public static void tick(ServerPlayer player) {
        if (!Frontier.isIn(player)) {
            Integer last = SENT.get(player.getUUID());
            if (last != null && last != 0) {
                PacketDistributor.sendToPlayer(player, new ModNetwork.Markers(List.of(), false));
                SENT.put(player.getUUID(), 0);
            }
            return;
        }
        ServerLevel level = player.serverLevel();
        PlayerData data = ModAttachments.get(player);
        discoverTowns(player, level, data);

        List<ModNetwork.Marker> markers = new ArrayList<>();
        Story.Target target = Story.compassTarget(player);
        if (target != null) markers.add(new ModNetwork.Marker("quest", target.nameKey(), ModNetwork.Marker.QUEST, target.pos()));
        Waypoints all = Waypoints.get(level);
        for (String id : data.waypoints()) {
            Waypoints.Waypoint wp = all.byId(id);
            if (wp == null) continue;
            int kind = wp.kind() == Waypoints.Kind.TOWN ? ModNetwork.Marker.TOWN : ModNetwork.Marker.WAYPOINT;
            markers.add(new ModNetwork.Marker(wp.id(), wp.nameKey(), kind, wp.pos()));
        }
        boolean fastTravel = SlfConfig.FAST_TRAVEL.get();
        int hash = markers.hashCode() * 31 + (fastTravel ? 1 : 2);
        Integer last = SENT.get(player.getUUID());
        if (last == null || last != hash) {
            SENT.put(player.getUUID(), hash);
            PacketDistributor.sendToPlayer(player, new ModNetwork.Markers(markers, fastTravel));
        }
    }

    private static void discoverTowns(ServerPlayer player, ServerLevel level, PlayerData data) {
        Waypoints all = Waypoints.get(level);
        for (Waypoints.Waypoint wp : all.all()) {
            if (wp.kind() != Waypoints.Kind.TOWN || data.knowsWaypoint(wp.id())) continue;
            BlockPos p = wp.pos();
            if (player.blockPosition().distToCenterSqr(p.getX(), player.getY(), p.getZ()) < TOWN_RADIUS * TOWN_RADIUS) {
                data.discoverWaypoint(wp.id());
                announceDiscovery(player, wp.nameKey());
            }
        }
    }

    public static void announceDiscovery(ServerPlayer player, String nameKey) {
        ModNetwork.announce(player, "announce.slfrontier.waypoint", nameKey, 0x7FE7FF);
        player.sendSystemMessage(Component.translatable("message.slfrontier.waypoint_found", Component.translatable(nameKey))
                .withStyle(ChatFormatting.AQUA));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.2f);
    }
}
