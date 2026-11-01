package com.slfrontier.client.model;

import com.slfrontier.client.anim.AnimSet;
import com.slfrontier.client.render.SlfRenderState;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A data-driven model: geometry comes from a generated layer definition, motion from
 * an {@link AnimSet} of keyframe animations. Used by every Frontier creature and NPC.
 */
public class SlfModel extends EntityModel<SlfRenderState> {
    /**
     * A per-creature extra on top of the animations: {@code flag} is read off the entity each
     * frame (into {@link SlfRenderState#flag}) and {@code pose} adjusts the model from it.
     */
    public record Hook<T>(Predicate<T> flag, BiConsumer<SlfRenderState, SlfModel> pose) {
    }

    private final AnimSet anims;
    private final Function<String, ModelPart> parts;
    @Nullable
    private final KeyframeAnimation walk;
    private final KeyframeAnimation[] slots;
    @Nullable
    private final BiConsumer<SlfRenderState, SlfModel> hook;

    public SlfModel(ModelPart root, AnimSet anims) {
        this(root, anims, null);
    }

    public SlfModel(ModelPart root, AnimSet anims, @Nullable BiConsumer<SlfRenderState, SlfModel> hook) {
        this(root, anims, hook, RenderTypes::entityCutout);
    }

    public SlfModel(ModelPart root, AnimSet anims, @Nullable BiConsumer<SlfRenderState, SlfModel> hook,
                    Function<Identifier, RenderType> renderType) {
        super(root, renderType);
        this.anims = anims;
        this.hook = hook;
        // Animations name parts; "root" is the generated root bone (pivot at the feet), not the mesh above it.
        ModelPart top = root.hasChild("root") ? root.getChild("root") : root;
        this.parts = top.createPartLookup();
        this.walk = anims.walk() == null ? null : bake(top, anims.walk());
        this.slots = new KeyframeAnimation[anims.slots().length];
        for (int i = 0; i < slots.length; i++) {
            if (anims.slots()[i] != null) slots[i] = bake(top, anims.slots()[i]);
        }
    }

    public Optional<ModelPart> part(String name) {
        return Optional.ofNullable(parts.apply(name));
    }

    /** Bakes an animation for this model, skipping parts it doesn't have (some sets are shared between models). */
    private KeyframeAnimation bake(ModelPart top, AnimationDefinition def) {
        Map<String, List<AnimationChannel>> bones = new HashMap<>(def.boneAnimations());
        bones.keySet().removeIf(name -> parts.apply(name) == null);
        return new AnimationDefinition(def.lengthInSeconds(), def.looping(), bones).bake(top);
    }

    @Override
    public void setupAnim(SlfRenderState state) {
        super.setupAnim(state);
        if (walk != null) {
            walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, anims.walkSpeed(), anims.walkScale());
        }
        // Slot times come held back by hit-stops, so a held creature resumes where it left off.
        for (int i = 0; i < slots.length && i < state.animTimes.length; i++) {
            if (slots[i] != null && state.animTimes[i] >= 0) slots[i].apply(state.animTimes[i], 1f);
        }
        if (anims.headPart() != null) {
            part(anims.headPart()).ifPresent(p -> {
                p.yRot += Mth.clamp(state.yRot, -60f, 60f) * Mth.DEG_TO_RAD * 0.8f;
                p.xRot += state.xRot * Mth.DEG_TO_RAD * 0.6f;
            });
        }
        if (hook != null) hook.accept(state, this);
    }
}
