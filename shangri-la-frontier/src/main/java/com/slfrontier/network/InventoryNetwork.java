package com.slfrontier.network;

import com.slfrontier.SLFrontier;
import com.slfrontier.inventory.BagSort;
import com.slfrontier.inventory.FrontierInventoryMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Packets of the Frontier inventory: opening it in place of the vanilla one, and sorting the bag. */
public final class InventoryNetwork {
    private InventoryNetwork() {
    }

    /** Client -> server: open the Frontier inventory (inventory key in the Frontier, or the Items tab). */
    public record OpenInventory() implements CustomPacketPayload {
        public static final Type<OpenInventory> TYPE = new Type<>(SLFrontier.id("open_inventory"));
        public static final StreamCodec<ByteBuf, OpenInventory> CODEC = StreamCodec.unit(new OpenInventory());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: sort the bag of the open Frontier inventory. */
    public record SortBag() implements CustomPacketPayload {
        public static final Type<SortBag> TYPE = new Type<>(SLFrontier.id("sort_bag"));
        public static final StreamCodec<ByteBuf, SortBag> CODEC = StreamCodec.unit(new SortBag());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(OpenInventory.TYPE, OpenInventory.CODEC, InventoryNetwork::handleOpen);
        registrar.playToServer(SortBag.TYPE, SortBag.CODEC, InventoryNetwork::handleSort);
    }

    private static void handleOpen(OpenInventory payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
                player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new FrontierInventoryMenu(id, inventory),
                        Component.translatable("gui.slfrontier.inventory")));
            }
        });
    }

    private static void handleSort(SortBag payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof FrontierInventoryMenu menu) {
                BagSort.sort(player.getInventory());
                menu.broadcastChanges();
            }
        });
    }
}
