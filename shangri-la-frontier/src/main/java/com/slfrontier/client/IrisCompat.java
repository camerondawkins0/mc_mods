package com.slfrontier.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.slfrontier.SLFrontier;
import com.slfrontier.client.fx.FxRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * With a shader pack on, Iris draws each render pipeline with a program from the pack, and
 * whatever it has no program for is lost under the pack's later passes. The mod's glowing
 * effects (skill slashes and rings, boss beams) and its decals (boss telegraphs, enemy health
 * bars) are plain coloured quads like vanilla lightning, so they get the program Iris gives
 * lightning.
 *
 * <p>Iris is optional, so it's reached by reflection: first its internal "draw this like that"
 * helper, which copies exactly what lightning gets, then its public API's basic program.
 */
public final class IrisCompat {
    private IrisCompat() {
    }

    static void init() {
        if (!ModList.get().isLoaded("iris")) return;
        for (RenderPipeline pipeline : List.of(FxRenderer.ADDITIVE_PIPELINE, FxRenderer.DECAL_PIPELINE)) {
            drawLike(RenderPipelines.LIGHTNING, pipeline);
        }
    }

    private static void drawLike(RenderPipeline vanilla, RenderPipeline ours) {
        try {
            Class.forName("net.irisshaders.iris.pipeline.IrisPipelines")
                    .getMethod("copyPipeline", RenderPipeline.class, RenderPipeline.class)
                    .invoke(null, vanilla, ours);
            SLFrontier.LOGGER.info("Iris draws {} like {}", ours.getLocation(), vanilla.getLocation());
            return;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Internal helper gone in this Iris version; use the public API.
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<?> program = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object basic = program.getMethod("valueOf", String.class).invoke(null, "BASIC");
            api.getMethod("assignPipeline", RenderPipeline.class, program).invoke(instance, ours, basic);
            SLFrontier.LOGGER.info("Iris draws {} with its basic program", ours.getLocation());
        } catch (ReflectiveOperationException | LinkageError e) {
            SLFrontier.LOGGER.warn("Couldn't hand {} to Iris; Frontier effects may not show under shader packs", ours.getLocation(), e);
        }
    }
}
