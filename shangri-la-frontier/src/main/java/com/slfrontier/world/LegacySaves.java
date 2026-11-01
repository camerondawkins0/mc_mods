package com.slfrontier.world;

import com.slfrontier.SLFrontier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Worlds saved by 0.2 (Minecraft 1.21.1) keep the Frontier's world-wide records in
 * {@code data/slfrontier_<name>.dat}. Minecraft 26.2 looks for saved data at
 * {@code data/<namespace>/<path>.dat} and only moves its own files there, so ours are moved once
 * when the world is first opened, before anything reads them.
 */
@EventBusSubscriber(modid = SLFrontier.MODID)
public final class LegacySaves {
    private LegacySaves() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Path data = event.getServer().getWorldPath(LevelResource.DATA);
        move(data, "slfrontier_towns", TownData.ID);
        move(data, "slfrontier_waypoints", Waypoints.ID);
    }

    private static void move(Path data, String oldName, Identifier id) {
        Path from = data.resolve(oldName + ".dat");
        Path to = id.withSuffix(".dat").resolveAgainst(data);
        if (!Files.isRegularFile(from) || Files.exists(to)) return;
        try {
            Files.createDirectories(to.getParent());
            Files.move(from, to);
            SLFrontier.LOGGER.info("Moved {} to {} for Minecraft 26.2", data.relativize(from), data.relativize(to));
        } catch (IOException e) {
            SLFrontier.LOGGER.error("Couldn't move {} to {}", from, to, e);
        }
    }
}
