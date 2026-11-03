package com.slfrontier.entity.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-player records for the bosses woken at altars (Lycagon's Shadow, the Nightslayer): each
 * altar answers a player at most once per in-game day, and a boss the player has already slain
 * comes back as a repeat fight (less EXP, no Tearjewel). Kept in the player's persisted data, so
 * it survives death and relogging.
 */
public final class BossAltar {
    public static final String SHADOW = "shadow", NIGHTSLAYER = "nightslayer";
    private static final String ROOT = "slfrontier_altars";

    private BossAltar() {
    }

    /** The in-game day number (the overworld clock drives the Frontier's sky too). */
    public static long today(ServerLevel level) {
        return level.getOverworldClockTime() / 24000L;
    }

    private static CompoundTag record(ServerPlayer player) {
        return player.getPersistentData().getCompoundOrEmpty(Player.PERSISTED_NBT_TAG).getCompoundOrEmpty(ROOT);
    }

    private static void save(ServerPlayer player, CompoundTag record) {
        CompoundTag persisted = player.getPersistentData().getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);
        persisted.put(ROOT, record);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Whether {@code player} already woke this altar today. */
    public static boolean usedToday(ServerPlayer player, String altar) {
        return record(player).getLongOr(altar + "_day", Long.MIN_VALUE) == today(player.level());
    }

    public static void markUsed(ServerPlayer player, String altar) {
        CompoundTag record = record(player);
        record.putLong(altar + "_day", today(player.level()));
        save(player, record);
    }

    public static boolean hasSlain(ServerPlayer player, String altar) {
        return record(player).getIntOr(altar + "_kills", 0) > 0;
    }

    public static void recordKill(ServerPlayer player, String altar) {
        CompoundTag record = record(player);
        record.putInt(altar + "_kills", record.getIntOr(altar + "_kills", 0) + 1);
        save(player, record);
    }

    /** Records the kill for everyone near the boss, plus whoever landed the final blow. */
    public static void recordKill(ServerLevel level, Entity boss, @Nullable Entity killer, String altar) {
        Set<ServerPlayer> players = new LinkedHashSet<>(level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(64)));
        if (killer instanceof ServerPlayer sp) players.add(sp);
        for (ServerPlayer p : players) recordKill(p, altar);
    }
}
