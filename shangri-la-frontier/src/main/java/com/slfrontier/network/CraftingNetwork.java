package com.slfrontier.network;

import com.slfrontier.SLFrontier;
import com.slfrontier.client.CraftingClient;
import com.slfrontier.crafting.FrontierCrafting;
import com.slfrontier.crafting.FrontierRecipe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/** Packets for Frontier crafting. The client only ever asks; the server decides. */
public final class CraftingNetwork {
    private CraftingNetwork() {
    }

    // ---- Server -> client ----------------------------------------------------------

    /** Every Frontier recipe, sent on login and after {@code /reload}. */
    public record SyncRecipes(List<FrontierRecipe> recipes) implements CustomPacketPayload {
        public static final Type<SyncRecipes> TYPE = new Type<>(SLFrontier.id("sync_recipes"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncRecipes> CODEC =
                FrontierRecipe.STREAM_CODEC.apply(ByteBufCodecs.list()).map(SyncRecipes::new, SyncRecipes::recipes);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Opens the crafting screen. {@code npcEntity} is -1 at a station block; {@code pos} is the
     * block (or the NPC's position) so the screen can close when the player walks away.
     */
    public record OpenCrafting(int station, int npcEntity, String npcId, BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenCrafting> TYPE = new Type<>(SLFrontier.id("open_crafting"));
        public static final StreamCodec<ByteBuf, OpenCrafting> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenCrafting::station,
                ByteBufCodecs.VAR_INT, OpenCrafting::npcEntity,
                ByteBufCodecs.STRING_UTF8, OpenCrafting::npcId,
                BlockPos.STREAM_CODEC, OpenCrafting::pos,
                OpenCrafting::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Progress of the player's craft. {@code message} is a translation key with arguments
     * ({@code key|arg|@argKey}) or empty.
     */
    public record CraftStatus(int state, ResourceLocation recipe, int quantity, int ticks, String message)
            implements CustomPacketPayload {
        public static final int STARTED = 0, DONE = 1, FAILED = 2, CANCELLED = 3;
        public static final Type<CraftStatus> TYPE = new Type<>(SLFrontier.id("craft_status"));
        public static final StreamCodec<ByteBuf, CraftStatus> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CraftStatus::state,
                ResourceLocation.STREAM_CODEC, CraftStatus::recipe,
                ByteBufCodecs.VAR_INT, CraftStatus::quantity,
                ByteBufCodecs.VAR_INT, CraftStatus::ticks,
                ByteBufCodecs.STRING_UTF8, CraftStatus::message,
                CraftStatus::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- Client -> server ----------------------------------------------------------

    /** Asks to craft a recipe {@code quantity} times at the station the player has open. */
    public record Craft(ResourceLocation recipe, int quantity) implements CustomPacketPayload {
        public static final Type<Craft> TYPE = new Type<>(SLFrontier.id("craft"));
        public static final StreamCodec<ByteBuf, Craft> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Craft::recipe,
                ByteBufCodecs.VAR_INT, Craft::quantity,
                Craft::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Stops the running craft (the screen was closed or Cancel was pressed). */
    public record CancelCraft() implements CustomPacketPayload {
        public static final Type<CancelCraft> TYPE = new Type<>(SLFrontier.id("cancel_craft"));
        public static final StreamCodec<ByteBuf, CancelCraft> CODEC = StreamCodec.unit(new CancelCraft());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- Registration --------------------------------------------------------------

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(SyncRecipes.TYPE, SyncRecipes.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> CraftingClient.setRecipes(payload.recipes())));
        registrar.playToClient(OpenCrafting.TYPE, OpenCrafting.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> CraftingClient.open(payload)));
        registrar.playToClient(CraftStatus.TYPE, CraftStatus.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> CraftingClient.status(payload)));

        registrar.playToServer(Craft.TYPE, Craft.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) FrontierCrafting.request(player, payload.recipe(), payload.quantity());
        }));
        registrar.playToServer(CancelCraft.TYPE, CancelCraft.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) FrontierCrafting.cancel(player);
        }));
    }
}
