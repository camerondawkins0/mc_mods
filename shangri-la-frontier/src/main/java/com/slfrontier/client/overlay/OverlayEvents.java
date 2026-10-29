package com.slfrontier.client.overlay;

import com.slfrontier.SLFrontier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Game-bus hooks for the overlays: ticking, world rendering and the travel screen. */
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
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            QuestPointer.renderWorld(event.getCamera(), pt);
            EnemyHealthBars.render(event.getCamera(), pt);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            QuestPointer.capture(event.getModelViewMatrix(), event.getProjectionMatrix(), event.getCamera());
        }
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
