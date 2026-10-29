package com.slfrontier.world;

import com.slfrontier.data.PlayerData;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.quest.QuestStage;
import com.slfrontier.quest.Story;
import com.slfrontier.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;


/** Logging in and out of the Frontier, and travelling between its towns. */
public final class FrontierTravel {
    private FrontierTravel() {
    }

    public static void login(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel frontier = server.getLevel(Frontier.LEVEL);
        if (frontier == null) {
            player.sendSystemMessage(Component.translatable("message.slfrontier.no_dimension").withStyle(ChatFormatting.RED));
            return;
        }
        PlayerData data = ModAttachments.get(player);
        data.setReturnPoint(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ());
        if (data.migrateToDesignedMap()) {
            player.sendSystemMessage(Component.translatable("message.slfrontier.new_map").withStyle(ChatFormatting.AQUA));
        }
        BlockPos spawn = ensureFastera(frontier);
        // Like any MMO, you log back in where you logged out.
        BlockPos target = data.hasFrontierPos() ? BlockPos.of(data.frontierPos()) : spawn;
        player.teleportTo(frontier, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, player.getYRot(), player.getXRot());
        frontier.playSound(null, target, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.6f);
        ModNetwork.announce(player, "announce.slfrontier.login", "announce.slfrontier.login.sub", 0x7FE7FF);
        if (data.stage() == QuestStage.NOT_STARTED) {
            Story.advance(player, QuestStage.MEET_GUIDE);
        }
    }

    public static void logout(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        PlayerData data = ModAttachments.get(player);
        data.setFrontierPos(player.blockPosition().asLong());
        ServerLevel dest = null;
        if (data.hasReturn()) {
            ResourceLocation id = ResourceLocation.tryParse(data.returnDim());
            if (id != null) dest = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        }
        double x, y, z;
        if (dest == null || dest.dimension() == Frontier.LEVEL) {
            dest = server.overworld();
            BlockPos spawn = dest.getSharedSpawnPos();
            x = spawn.getX() + 0.5;
            y = spawn.getY();
            z = spawn.getZ() + 0.5;
        } else {
            x = data.returnX();
            y = data.returnY();
            z = data.returnZ();
        }
        player.teleportTo(dest, x, y, z, player.getYRot(), player.getXRot());
        ModNetwork.announce(player, "announce.slfrontier.logout", "announce.slfrontier.logout.sub", 0xAAAAAA);
    }

    public static void toFastera(ServerPlayer player) {
        ServerLevel frontier = frontierLevel(player);
        if (frontier == null) return;
        BlockPos spawn = ensureFastera(frontier);
        player.teleportTo(frontier, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    public static void toWaypoint(ServerPlayer player, Waypoints.Waypoint wp) {
        ServerLevel frontier = frontierLevel(player);
        if (frontier == null) return;
        BlockPos pos = FastTravel.arrival(frontier, wp);
        player.teleportTo(frontier, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    public static void toRabituza(ServerPlayer player) {
        ServerLevel frontier = frontierLevel(player);
        if (frontier == null) return;
        BlockPos pos = ensureRabituza(frontier);
        player.teleportTo(frontier, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
        frontier.playSound(null, pos, SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 1f, 0.8f);
        player.displayClientMessage(Component.translatable("message.slfrontier.arrive_rabituza").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    @Nullable
    private static ServerLevel frontierLevel(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : server.getLevel(Frontier.LEVEL);
    }

    /** Firstia (internally "fastera"), the starting town at the heart of the map. */
    public static BlockPos ensureFastera(ServerLevel frontier) {
        return Landmarks.ensure(frontier, Landmarks.Site.FIRSTIA);
    }

    /** Rabituza, the hidden nation of the Vorpal Bunnies, on its island out at sea. */
    public static BlockPos ensureRabituza(ServerLevel frontier) {
        return Landmarks.ensure(frontier, Landmarks.Site.RABITUZA);
    }

    public static void toSite(ServerPlayer player, Landmarks.Site site) {
        ServerLevel frontier = frontierLevel(player);
        if (frontier == null) return;
        BlockPos pos = Landmarks.ensure(frontier, site);
        player.teleportTo(frontier, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    /** Towns are fast-travel waypoints; also fills them in for worlds made before waypoints existed. */
    public static void registerTownWaypoints(ServerLevel level) {
        Landmarks.registerWaypoints(level);
    }

    public static boolean isFrontierLevel(Level level) {
        return level.dimension() == Frontier.LEVEL;
    }
}
