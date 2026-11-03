package com.slfrontier.client;

import com.slfrontier.SLFrontier;
import com.slfrontier.client.map.MapStore;
import com.slfrontier.quest.QuestStage;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModItems;
import com.slfrontier.world.Frontier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A one-time pointer for new players: a little while after first joining a world, if they have
 * never been to the Frontier and have no headset, one chat line says how to get in. Remembered per
 * world in the map folder, so it never repeats there. (The recipe itself unlocks in the recipe book
 * through the slfrontier:recipes/tools/fulldive_headset advancement.)
 */
@EventBusSubscriber(modid = SLFrontier.MODID, value = Dist.CLIENT)
public final class Onboarding {
    /** Ticks of actual play (no screen open) after joining before the hint, so it isn't lost in join chatter. */
    private static final int DELAY = 20 * 8;
    private static final String MARKER = "hint_headset";
    private static int wait = -1;

    private Onboarding() {
    }

    @SubscribeEvent
    public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        wait = DELAY;
    }

    @SubscribeEvent
    public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        wait = -1;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (wait < 0 || ClientTestDriver.ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.screen() != null || mc.isPaused()) return;
        if (--wait > 0) return;
        wait = -1;
        Path dir = MapStore.worldFolder();
        if (dir == null) return;
        Path marker = dir.resolve(MARKER);
        if (Files.exists(marker)) return;
        boolean newcomer = !Frontier.isIn(player) && ModAttachments.get(player).stage() == QuestStage.NOT_STARTED
                && !player.getInventory().contains(stack -> stack.is(ModItems.FULLDIVE_HEADSET.get()));
        if (newcomer) {
            Component headset = Component.translatable("hint.slfrontier.craft_headset").withStyle(ChatFormatting.AQUA);
            mc.gui.hud.getChat().addClientSystemMessage(headset);
        }
        // Either way the player has now been told (or plainly knows): never again in this world.
        try {
            Files.createDirectories(dir);
            Files.writeString(marker, "shown\n");
        } catch (IOException e) {
            SLFrontier.LOGGER.warn("Could not remember the Frontier hint", e);
        }
    }
}
