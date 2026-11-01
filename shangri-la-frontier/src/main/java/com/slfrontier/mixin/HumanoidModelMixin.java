package com.slfrontier.mixin;

import com.slfrontier.client.ClientAnims;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bends player limbs into skill poses after vanilla has set up the model. Only players' render
 * states carry an animation sample, and armour models are set up from the same state, so they follow.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void slfrontier$skillPose(HumanoidRenderState state, CallbackInfo ci) {
        ClientAnims.apply((HumanoidModel<?>) (Object) this, state);
    }
}
