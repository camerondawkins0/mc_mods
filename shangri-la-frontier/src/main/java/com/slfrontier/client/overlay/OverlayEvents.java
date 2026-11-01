package com.slfrontier.client.overlay;

import com.slfrontier.SLFrontier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Game-bus hooks for the overlays: ticking and the travel screen. The pointer and health bars
 * are drawn in the world from {@code ClientEvents}' level render-state hooks.
 */
@EventBusSubscriber(modid = SLFrontier.MODID, value = Dist.CLIENT)
public final class OverlayEvents {
    private OverlayEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        QuestPointer.tick();
        EnemyHealthBars.tick();
        HudNotice.tick();
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        TravelOverlay.onScreenOpening(event);
    }

    @SubscribeEvent
    public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        QuestPointer.clear();
        EnemyHealthBars.clear();
        HudNotice.clear();
        TravelOverlay.reset();
    }
}
