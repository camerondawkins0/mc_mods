package com.slfrontier.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slfrontier.client.FirstPersonAnims;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves the first-person hands through skill animations (inside vanilla's push/pop for each hand). */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            shift = At.Shift.AFTER, ordinal = 0))
    private void slfrontier$skillHands(AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand,
                                       float swingProgress, ItemStack stack, float equipProgress, PoseStack pose,
                                       MultiBufferSource buffers, int light, CallbackInfo ci) {
        FirstPersonAnims.apply(player, hand, pose, partialTicks, swingProgress);
    }
}
