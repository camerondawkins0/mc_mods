package com.slfrontier.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slfrontier.client.fx.Impact;
import com.slfrontier.client.model.SlfModel;
import com.slfrontier.entity.SlfAnimated;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Renderer for {@link SlfModel} creatures with a fixed texture and size. Parts painted
 * with glowing materials also live in {@code <texture>_glow.png}, which is drawn full
 * bright on top (eyes, Wezaemon's visor, Kirin's cables...).
 */
public class SlfMobRenderer<T extends Mob & SlfAnimated> extends MobRenderer<T, SlfRenderState, SlfModel> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private final Identifier texture;
    private final float scale;
    @Nullable
    private final Predicate<T> flag;

    public SlfMobRenderer(EntityRendererProvider.Context ctx, SlfModel model, Identifier texture, float shadow, float scale) {
        this(ctx, model, texture, shadow, scale, null);
    }

    public SlfMobRenderer(EntityRendererProvider.Context ctx, SlfModel model, Identifier texture, float shadow, float scale,
                          @Nullable Predicate<T> flag) {
        super(ctx, model, shadow * scale);
        this.texture = texture;
        this.scale = scale;
        this.flag = flag;
        addLayer(new GlowLayer(this, texture));
        addLayer(new FlashLayer(this, texture));
    }

    @Override
    public SlfRenderState createRenderState() {
        return new SlfRenderState();
    }

    @Override
    public void extractRenderState(T entity, SlfRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        AnimationState[] anims = entity.animStates();
        for (int i = 0; i < state.animTimes.length; i++) {
            AnimationState anim = i < anims.length ? anims[i] : null;
            state.animTimes[i] = anim != null && anim.isStarted()
                    ? Impact.heldBack(entity.getId(), anim.getTimeInMillis(state.ageInTicks), partialTick) : -1;
        }
        state.flash = Impact.flash(entity.getId(), partialTick);
        state.flag = flag != null && flag.test(entity);
    }

    @Override
    public Identifier getTextureLocation(SlfRenderState state) {
        return texture;
    }

    @Override
    protected void scale(SlfRenderState state, PoseStack poseStack) {
        if (scale != 1f) poseStack.scale(scale, scale, scale);
    }

    /**
     * The white flash of a fresh hit: the model drawn again, washed white and fading out.
     * (Vanilla's white overlay would be tinted red by the hurt overlay, so it's a second pass.)
     */
    static final class FlashLayer extends RenderLayer<SlfRenderState, SlfModel> {
        private static final int WHITE_OVERLAY = OverlayTexture.pack(15, 10);
        private final Identifier texture;

        FlashLayer(RenderLayerParent<SlfRenderState, SlfModel> parent, Identifier texture) {
            super(parent);
            this.texture = texture;
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, SlfRenderState state, float yRot, float xRot) {
            if (state.flash <= 0 || state.isInvisible) return;
            // A later order than the glow layer, so the flash always washes over it.
            collector.order(2).submitModel(getParentModel(), state, pose, RenderTypes.entityTranslucent(texture), FULL_BRIGHT, WHITE_OVERLAY,
                    (int) (state.flash * 235) << 24 | 0xFFFFFF, null, 0, null);
        }
    }

    /** Draws the emissive {@code _glow} texture, if the creature has one. */
    static final class GlowLayer extends RenderLayer<SlfRenderState, SlfModel> {
        private final Identifier glow;
        private Boolean present;

        GlowLayer(RenderLayerParent<SlfRenderState, SlfModel> parent, Identifier texture) {
            super(parent);
            String path = texture.getPath();
            this.glow = texture.withPath(path.substring(0, path.length() - 4) + "_glow.png");
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, SlfRenderState state, float yRot, float xRot) {
            if (present == null) present = Minecraft.getInstance().getResourceManager().getResource(glow).isPresent();
            if (!present || state.isInvisible) return;
            collector.order(1).submitModel(getParentModel(), state, pose, RenderTypes.eyes(glow), FULL_BRIGHT,
                    LivingEntityRenderer.getOverlayCoords(state, 0f), -1, null, 0, null);
        }
    }
}
