package com.slfrontier.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * Submits the world overlays (effects, health bars, the quest marker and floating text) to be
 * drawn after translucent terrain and particles, where they always were: glows keep their
 * brightness over water instead of being washed out by it. Each overlay gets its own order, so
 * they paint in a fixed sequence (geometry sharing an order may draw in any order).
 */
public final class WorldLayer {
    public static final int DECALS = 100, EFFECTS = 101, MARKER = 102, MARKER_GLOW = 103, BARS = 104, TEXT = 105;
    private static final int FULL_BRIGHT = 0xF000F0;

    private WorldLayer() {
    }

    /** Custom geometry, drawn by {@code renderer} with the pose's matrix once the frame is prepared. */
    public static void geometry(SubmitNodeCollector collector, int order, PoseStack pose, RenderType type,
                                SubmitNodeCollector.CustomGeometryRenderer renderer) {
        if (collector instanceof SubmitNodeStorage storage) {
            storage.order(order).afterTerrain.submit(new CustomFeatureRenderer.Submit(pose.last().copy(), type, renderer));
        } else {
            collector.order(order).submitCustomGeometry(pose, type, renderer);
        }
    }

    /** Full-bright text at (x, y) in the pose's plane. */
    public static void text(SubmitNodeCollector collector, PoseStack pose, float x, float y, Component text, boolean shadow,
                            Font.DisplayMode mode, int color, int background) {
        if (collector instanceof SubmitNodeStorage storage) {
            storage.order(TEXT).afterTerrain.submit(new TextFeatureRenderer.Submit(new Matrix4f(pose.last().pose()), x, y,
                    text.getVisualOrderText(), shadow, mode, FULL_BRIGHT, color, background, 0));
        } else {
            collector.order(TEXT).submitText(pose, x, y, text.getVisualOrderText(), shadow, mode, FULL_BRIGHT, color, background, 0);
        }
    }
}
