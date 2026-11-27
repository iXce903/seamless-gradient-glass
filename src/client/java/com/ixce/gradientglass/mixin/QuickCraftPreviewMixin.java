package com.ixce.gradientglass.mixin;

import com.ixce.gradientglass.GradientGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.yiyihehe.quickcraft.litematica.QuickLitematicaPreviewCompat", remap = false)
abstract class QuickCraftPreviewMixin {
    // QuickCraft uses this check to select its Fabric Renderer path for the preview.
    @Inject(method = "isContinuityModel(Ljava/lang/Object;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gradientglass$useFabricRenderer(Object model, CallbackInfoReturnable<Boolean> cir) {
        if (GradientGlass.isGradientModel(model)) cir.setReturnValue(true);
    }
}
