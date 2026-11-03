package com.slfrontier.progression;

import com.slfrontier.entity.FrontierBoss;
import com.slfrontier.entity.npc.AllyNpc;
import com.slfrontier.entity.npc.EmulEntity;
import com.slfrontier.world.Frontier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Who gets the experience for a kill: everyone who hurt the monster recently, not only whoever
 * landed the last blow. A boss finished off by Pencilgon or Oikatzo, a monster Emul's Magic Edge
 * finishes, or a co-op partner's kill still pays every player who fought it.
 */
public final class KillCredit {
    /** How long a hit keeps counting toward the kill: a minute for monsters, the whole fight for a boss. */
    private static final long WINDOW = 20 * 60, BOSS_WINDOW = 20 * 60 * 10;
    /** Players further than this from the kill (or in another dimension) get nothing. */
    private static final double RANGE = 96;
    private static final Map<LivingEntity, Map<UUID, Long>> HITS = new WeakHashMap<>();

    private KillCredit() {
    }

    /** Remembers that a player hurt the entity (called for every hit that does damage). */
    public static void record(LivingEntity victim, ServerPlayer player) {
        HITS.computeIfAbsent(victim, k -> new HashMap<>()).put(player.getUUID(), victim.level().getGameTime());
    }

    /** The players to credit for a death; forgets the entity's hit record. */
    public static Set<ServerPlayer> creditFor(LivingEntity dead, @Nullable Entity killer) {
        Set<ServerPlayer> players = new LinkedHashSet<>();
        if (killer instanceof ServerPlayer sp) players.add(sp);
        Map<UUID, Long> hits = HITS.remove(dead);
        if (!(dead.level() instanceof ServerLevel level)) return players;
        long now = level.getGameTime();
        long window = dead instanceof FrontierBoss ? BOSS_WINDOW : WINDOW;
        if (hits != null) {
            hits.forEach((id, at) -> {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null && now - at <= window && near(p, dead)) players.add(p);
            });
        }
        if (players.isEmpty() && dead.getLastHurtByPlayer() instanceof ServerPlayer last && near(last, dead)) players.add(last);
        // An ally or Emul finished a monster nobody else touched: whoever they were fighting beside.
        if (players.isEmpty() && (killer instanceof AllyNpc || killer instanceof EmulEntity)) {
            for (Player p : level.getEntitiesOfClass(Player.class, dead.getBoundingBox().inflate(48))) {
                if (p instanceof ServerPlayer sp && sp.isAlive() && !sp.isSpectator()) players.add(sp);
            }
        }
        players.removeIf(p -> !Frontier.isIn(p));
        return players;
    }

    private static boolean near(ServerPlayer player, LivingEntity dead) {
        return player.level() == dead.level() && player.distanceToSqr(dead) <= RANGE * RANGE;
    }
}
