package com.slfrontier.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Humanoid creatures and NPCs drawn with the vanilla biped model and a custom skin. */
public class NpcHumanoidRenderer<T extends Mob> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
    private final ResourceLocation texture;
    private final float scale;

    public NpcHumanoidRenderer(EntityRendererProvider.Context ctx, HumanoidModel<T> model, ResourceLocation texture, float scale) {
        super(ctx, model, 0.5f * scale);
        this.texture = texture;
        this.scale = scale;
    }

    /** A see-through biped, for ghosts like Setsuna. */
    public static <T extends Mob> HumanoidModel<T> ghostModel(ModelPart root) {
        return new HumanoidModel<>(root, RenderType::entityTranslucent) {
            @Override
            public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
                super.renderToBuffer(poseStack, buffer, light, overlay, (color & 0x00FFFFFF) | 0xA0000000);
            }
        };
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
