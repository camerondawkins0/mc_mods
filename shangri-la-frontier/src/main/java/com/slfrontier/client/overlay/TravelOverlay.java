package com.slfrontier.client.overlay;

import com.slfrontier.SlfConfig;
import com.slfrontier.network.OverlayNetwork;
import com.slfrontier.world.Frontier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/**
 * State of the travel loading screen. A trip starts when the server announces a teleport (or
 * the player changes dimension into the Frontier) and outlives the screens that show it:
 * vanilla swaps its terrain screen several times during a dimension change, and every new
 * {@link TravelScreen} picks up the running trip instead of starting over.
 */
public final class TravelOverlay {
    static final long MIN_MS = 1500;
    static final long TIMEOUT_MS = 10_000;
    static final long FADE_IN_MS = 180;
    static final long FADE_OUT_MS = 450;
    /** Ticks the chunks around the player must have been loaded before the section check means anything. */
    private static final int SETTLE_TICKS = 6;
    /** A trip with a known destination ends only once the player is this close to it. */
    private static final int ARRIVAL_RADIUS = 32;

    /** One trip: what to show and how far the arrival has got. */
    static final class Trip {
        final Component header;
        final Component destination;
        @Nullable
        final BlockPos dest;
        final boolean lore;
        final Component tip;
        final long start = Util.getMillis();
        long fadeStart = -1;
        int settled;
        boolean chunksLoaded;
        boolean arrived;
        float progress;

        Trip(int kind, String nameKey, @Nullable BlockPos dest) {
            this.header = Component.translatable(kind == OverlayNetwork.LOGIN ? "travel.slfrontier.header.login" : "travel.slfrontier.header");
            this.destination = Component.translatable(nameKey);
            this.dest = dest;
            int tips = count("travel.slfrontier.tip."), facts = count("travel.slfrontier.lore.");
            int pick = pickTip(tips + facts);
            this.lore = pick >= tips;
            this.tip = pick < 0 ? Component.empty()
                    : Component.translatable(lore ? "travel.slfrontier.lore." + (pick - tips + 1) : "travel.slfrontier.tip." + (pick + 1));
        }

        long age() {
            return Util.getMillis() - start;
        }

        /** Opacity of the whole screen: fully opaque until the fade-out starts. */
        float screenAlpha() {
            return fadeStart < 0 ? 1f : Mth.clamp(1f - (Util.getMillis() - fadeStart) / (float) FADE_OUT_MS, 0f, 1f);
        }

        float contentAlpha() {
            return Math.min(screenAlpha(), Mth.clamp(age() / (float) FADE_IN_MS, 0f, 1f));
        }
    }

    @Nullable
    private static Trip trip;
    private static int lastTip = -1;

    private TravelOverlay() {
    }

    /** The server is about to move the player: cover the jump. */
    public static void begin(OverlayNetwork.TravelStart payload) {
        if (!SlfConfig.TRAVEL_SCREEN.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        trip = new Trip(payload.kind(), payload.nameKey(), payload.dest().orElse(null));
        mc.setScreen(new TravelScreen(() -> true, ReceivingLevelScreen.Reason.OTHER));
    }

    /** True while the travel screen is up (HUD announcements wait for it). */
    public static boolean isActive() {
        return trip != null && Minecraft.getInstance().screen instanceof TravelScreen;
    }

    @Nullable
    static Trip trip() {
        return trip;
    }

    /** NeoForge's terrain-screen factory for dimension changes into the Frontier. */
    static ReceivingLevelScreen create(BooleanSupplier levelReady, ReceivingLevelScreen.Reason reason) {
        if (!SlfConfig.TRAVEL_SCREEN.get()) return new ReceivingLevelScreen(levelReady, reason);
        if (!isActive() || trip.age() > TIMEOUT_MS) trip = new Trip(OverlayNetwork.TRAVEL, "travel.slfrontier.frontier", null);
        return new TravelScreen(levelReady, reason);
    }

    /** Keeps vanilla's "Loading terrain" from replacing a running trip (respawning at a waypoint does this). */
    static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof ReceivingLevelScreen screen && !(screen instanceof TravelScreen) && isActive()) {
            event.setNewScreen(new TravelScreen(() -> true, ReceivingLevelScreen.Reason.OTHER));
        }
    }

    static void reset() {
        trip = null;
    }

    /** Called every client tick by the open travel screen; closes it once the trip is over. */
    static void tick(TravelScreen screen, BooleanSupplier levelReady) {
        Trip t = trip;
        if (t == null) {
            screen.finish();
            return;
        }
        if (t.fadeStart < 0) {
            boolean arrived = levelReady.getAsBoolean() && destinationReady(t);
            t.arrived |= arrived;
            if ((arrived && t.age() >= MIN_MS) || t.age() >= TIMEOUT_MS) t.fadeStart = Util.getMillis();
        } else if (Util.getMillis() - t.fadeStart >= FADE_OUT_MS) {
            trip = null;
            screen.finish();
        }
    }

    /** The player stands at the destination and the terrain around them has been loaded and built. */
    private static boolean destinationReady(Trip t) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return false;
        if (t.dest != null) {
            if (!Frontier.isIn(player)) return false;
            double dx = player.getX() - (t.dest.getX() + 0.5), dz = player.getZ() - (t.dest.getZ() + 0.5);
            if (dx * dx + dz * dz > ARRIVAL_RADIUS * ARRIVAL_RADIUS) return false;
        }
        BlockPos pos = player.blockPosition();
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (!level.getChunkSource().hasChunk(cx + x, cz + z)) {
                    t.settled = 0;
                    return false;
                }
            }
        }
        t.chunksLoaded = true;
        if (++t.settled < SETTLE_TICKS) return false;
        return level.isOutsideBuildHeight(pos.getY()) || mc.levelRenderer.isSectionCompiled(pos);
    }

    private static int count(String prefix) {
        int n = 0;
        while (I18n.exists(prefix + (n + 1))) n++;
        return n;
    }

    /** A random tip or fact, never the one shown last time. */
    private static int pickTip(int total) {
        if (total <= 0) return -1;
        int pick = ThreadLocalRandom.current().nextInt(total);
        if (pick == lastTip && total > 1) pick = (pick + 1 + ThreadLocalRandom.current().nextInt(total - 1)) % total;
        lastTip = pick;
        return pick;
    }
}
