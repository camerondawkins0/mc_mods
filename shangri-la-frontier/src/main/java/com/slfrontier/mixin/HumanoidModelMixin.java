package com.slfrontier.mixin;

import com.slfrontier.client.ClientAnims;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bends player limbs into skill poses after vanilla has set up the model. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void slfrontier$skillPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                      float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof Player) {
            ClientAnims.apply((HumanoidModel<?>) (Object) this, entity, ageInTicks);
        }
    }
}
