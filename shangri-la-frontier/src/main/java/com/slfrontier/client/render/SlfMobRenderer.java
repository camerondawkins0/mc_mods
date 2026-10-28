package com.slfrontier.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.slfrontier.client.model.SlfModel;
import com.slfrontier.entity.SlfAnimated;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * Renderer for {@link SlfModel} creatures with a fixed texture and size. Parts painted
 * with glowing materials also live in {@code <texture>_glow.png}, which is drawn full
 * bright on top (eyes, Wezaemon's visor, Kirin's cables...).
 */
public class SlfMobRenderer<T extends Mob & SlfAnimated> extends MobRenderer<T, SlfModel<T>> {
    private final ResourceLocation texture;
    private final float scale;

    public SlfMobRenderer(EntityRendererProvider.Context ctx, SlfModel<T> model, ResourceLocation texture, float shadow, float scale) {
        super(ctx, model, shadow * scale);
        this.texture = texture;
        this.scale = scale;
        addLayer(new GlowLayer<>(this, texture));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        if (scale != 1f) poseStack.scale(scale, scale, scale);
    }

    /** Draws the emissive {@code _glow} texture, if the creature has one. */
    static final class GlowLayer<T extends Mob & SlfAnimated> extends RenderLayer<T, SlfModel<T>> {
        private final ResourceLocation glow;
        private Boolean present;

        GlowLayer(RenderLayerParent<T, SlfModel<T>> parent, ResourceLocation texture) {
            super(parent);
            String path = texture.getPath();
            this.glow = texture.withPath(path.substring(0, path.length() - 4) + "_glow.png");
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (present == null) present = Minecraft.getInstance().getResourceManager().getResource(glow).isPresent();
            if (!present || entity.isInvisible()) return;
            VertexConsumer buffer = buffers.getBuffer(RenderType.eyes(glow));
            getParentModel().renderToBuffer(pose, buffer, 0xF000F0, LivingEntityRenderer.getOverlayCoords(entity, 0f));
        }
    }
}
