package com.slfrontier.event;

import com.slfrontier.SLFrontier;
import com.slfrontier.registry.ModEntities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

@EventBusSubscriber(modid = SLFrontier.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModBusEvents {
    private ModBusEvents() {
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        ModEntities.registerAttributes(event);
    }

    @SubscribeEvent
    public static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        ModEntities.registerSpawnPlacements(event);
    }
}
