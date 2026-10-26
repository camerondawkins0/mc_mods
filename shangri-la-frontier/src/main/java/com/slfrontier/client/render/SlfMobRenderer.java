package com.slfrontier.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slfrontier.client.model.SlfModel;
import com.slfrontier.entity.SlfAnimated;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Renderer for {@link SlfModel} creatures with a fixed texture and size. */
public class SlfMobRenderer<T extends Mob & SlfAnimated> extends MobRenderer<T, SlfModel<T>> {
    private final ResourceLocation texture;
    private final float scale;

    public SlfMobRenderer(EntityRendererProvider.Context ctx, SlfModel<T> model, ResourceLocation texture, float shadow, float scale) {
        super(ctx, model, shadow);
        this.texture = texture;
        this.scale = scale;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        if (scale != 1f) poseStack.scale(scale, scale, scale);
    }
}
