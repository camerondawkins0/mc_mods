package com.slfrontier.mixin;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import com.slfrontier.world.LegacyEntities;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.fixes.References;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Upgrades the Frontier's creatures along with vanilla's when an old entity chunk loads; see {@link LegacyEntities}. */
@Mixin(DataFixTypes.class)
public abstract class DataFixTypesMixin {
    @Inject(method = "update(Lcom/mojang/datafixers/DataFixer;Lcom/mojang/serialization/Dynamic;II)Lcom/mojang/serialization/Dynamic;",
            at = @At("HEAD"), cancellable = true)
    private <T> void slfrontier$upgradeFrontierEntities(DataFixer fixer, Dynamic<T> input, int fromVersion, int toVersion,
                                                         CallbackInfoReturnable<Dynamic<T>> cir) {
        if ((Object) this != DataFixTypes.ENTITY_CHUNK || fromVersion >= toVersion) return;
        Dynamic<T> disguised = LegacyEntities.disguise(input);
        if (disguised == null) return;
        cir.setReturnValue(LegacyEntities.reveal(fixer.update(References.ENTITY_CHUNK, disguised, fromVersion, toVersion)));
    }
}
