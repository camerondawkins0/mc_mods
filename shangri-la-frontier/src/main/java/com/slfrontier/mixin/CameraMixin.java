package com.slfrontier.mixin;

import com.slfrontier.client.fx.Impact;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Heavy hits punch the field of view in for a moment (scaled by the FOV Effects slider). Only the
 * world's FOV: NeoForge's ComputeFov event also fires for the hand's, and can't tell them apart.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void slfrontier$fovPunch(float partialTicks, CallbackInfoReturnable<Float> cir) {
        float punch = Impact.fovPunchAt(partialTicks);
        if (punch > 0) cir.setReturnValue(cir.getReturnValueF() * (1 - punch));
    }
}
