package com.slfrontier.client.overlay;

import com.slfrontier.SLFrontier;
import com.slfrontier.world.Frontier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Mod-bus registration for the overlays: HUD layers and the Frontier's loading screen. */
@EventBusSubscriber(modid = SLFrontier.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class OverlaySetup {
    private OverlaySetup() {
    }

    @SubscribeEvent
    public static void registerGui(RegisterGuiLayersEvent event) {
        // Below the hotbar and the Frontier HUD panels, above the crosshair.
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, SLFrontier.id("quest_pointer"), QuestPointer::renderHud);
        event.registerAboveAll(SLFrontier.id("hud_notice"), HudNotice::render);
    }

    /** Dimension changes into the Frontier get the travel screen instead of "Loading terrain". */
    @SubscribeEvent
    public static void registerTransitions(RegisterDimensionTransitionScreenEvent event) {
        event.registerIncomingEffect(Frontier.LEVEL, TravelOverlay::create);
    }
}
