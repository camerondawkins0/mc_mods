package com.slfrontier.network;

import com.slfrontier.SLFrontier;
import com.slfrontier.client.overlay.TravelOverlay;
import com.slfrontier.world.Waypoints;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Packets for the client overlays (the travel loading screen). */
public final class OverlayNetwork {
    /** Travel screen headers: "Traveling to" and "Logging in to". */
    public static final int TRAVEL = 0, LOGIN = 1;
    private static final int NEAR_WAYPOINT = 96;

    private OverlayNetwork() {
    }

    /**
     * Sent right before the server moves a player to {@code dest}, so the client can cover the
     * jump with the travel screen. {@code kind} picks the header ({@link #TRAVEL}, {@link #LOGIN}).
     */
    public record TravelStart(int kind, String nameKey, Optional<BlockPos> dest) implements CustomPacketPayload {
        public static final Type<TravelStart> TYPE = new Type<>(SLFrontier.id("travel_start"));
        public static final StreamCodec<ByteBuf, TravelStart> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TravelStart::kind,
                ByteBufCodecs.STRING_UTF8, TravelStart::nameKey,
                ByteBufCodecs.optional(BlockPos.STREAM_CODEC), TravelStart::dest,
                TravelStart::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(TravelStart.TYPE, TravelStart.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> TravelOverlay.begin(payload)));
    }

    /** Tells the player's client a travel to {@code dest} (if known) named {@code nameKey} is about to happen. */
    public static void travel(ServerPlayer player, int kind, String nameKey, @Nullable BlockPos dest) {
        PacketDistributor.sendToPlayer(player, new TravelStart(kind, nameKey, Optional.ofNullable(dest)));
    }

    /** A name for an arbitrary spot: the nearest waypoint if one is close, otherwise its biome. */
    public static String placeName(ServerLevel level, BlockPos pos) {
        Waypoints.Waypoint best = null;
        double bestDist = NEAR_WAYPOINT * NEAR_WAYPOINT;
        for (Waypoints.Waypoint wp : Waypoints.get(level).all()) {
            double d = wp.pos().distSqr(pos);
            if (d < bestDist) {
                bestDist = d;
                best = wp;
            }
        }
        if (best != null) return best.nameKey();
        return level.getBiome(pos).unwrapKey()
                .map(k -> "biome." + k.location().getNamespace() + "." + k.location().getPath())
                .orElse("travel.slfrontier.frontier");
    }
}
