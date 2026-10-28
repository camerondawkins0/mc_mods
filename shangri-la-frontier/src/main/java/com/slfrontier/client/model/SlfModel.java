package com.slfrontier.client.model;

import com.slfrontier.client.anim.AnimSet;
import com.slfrontier.entity.SlfAnimated;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A data-driven model: geometry comes from a generated layer definition, motion from
 * an {@link AnimSet} of keyframe animations. Used by every Frontier creature and NPC.
 */
public class SlfModel<T extends Mob & SlfAnimated> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final AnimSet anims;
    @Nullable
    private final BiConsumer<T, SlfModel<T>> hook;

    public SlfModel(ModelPart root, AnimSet anims) {
        this(root, anims, null);
    }

    public SlfModel(ModelPart root, AnimSet anims, @Nullable BiConsumer<T, SlfModel<T>> hook) {
        this(root, anims, hook, RenderType::entityCutoutNoCull);
    }

    public SlfModel(ModelPart root, AnimSet anims, @Nullable BiConsumer<T, SlfModel<T>> hook,
                    Function<ResourceLocation, RenderType> renderType) {
        super(renderType);
        this.root = root;
        this.anims = anims;
        this.hook = hook;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    public Optional<ModelPart> part(String name) {
        return getAnyDescendantWithName(name);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        if (anims.walk() != null) {
            animateWalk(anims.walk(), limbSwing, limbSwingAmount, anims.walkSpeed(), anims.walkScale());
        }
        AnimationState[] states = entity.animStates();
        for (int i = 0; i < anims.slots().length && i < states.length; i++) {
            if (anims.slots()[i] != null) animate(states[i], anims.slots()[i], ageInTicks);
        }
        if (anims.headPart() != null) {
            part(anims.headPart()).ifPresent(p -> {
                p.yRot += Mth.clamp(netHeadYaw, -60f, 60f) * Mth.DEG_TO_RAD * 0.8f;
                p.xRot += headPitch * Mth.DEG_TO_RAD * 0.6f;
            });
        }
        if (hook != null) hook.accept(entity, this);
    }
}
