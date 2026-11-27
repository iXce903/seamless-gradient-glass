package com.ixce.gradientglass.mixin;

import com.ixce.gradientglass.config.GlassConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Geometry is submitted now and drawn later, so the color below is carried into the deferred renderer.
@Mixin(BeaconRenderer.class)
abstract class BeaconRendererMixin {
    @Unique
    private static int submittingBelow, drawingBelow;

    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/BeaconRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BeaconRenderer;submitBeaconBeam(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;FFIII)V"))
    private void gradientglass$section(PoseStack poses, SubmitNodeCollector collector, float scale, float time,
                                       int bottom, int height, int color, Operation<Void> original,
                                       @Local(argsOnly = true) BeaconRenderState state) {
        submittingBelow = GlassConfig.beaconGradient() ? colorBelow(state, bottom, color) : 0;
        try {
            original.call(poses, collector, scale, time, bottom, height, color);
        } finally {
            submittingBelow = 0;
        }
    }

    @Unique
    private static int colorBelow(BeaconRenderState state, int bottom, int color) {
        int y = 0, below = 0;
        for (var section : state.sections) {
            if (y == bottom) return below != color ? below : 0;
            below = section.color();
            y += section.height();
        }
        return 0;
    }

    @WrapOperation(method = "submitBeaconBeam(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/resources/Identifier;FFIIIFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"),
            require = 2)
    private static void gradientglass$geometry(SubmitNodeCollector collector, PoseStack poses, RenderType type,
                                               SubmitNodeCollector.CustomGeometryRenderer renderer, Operation<Void> original) {
        int below = submittingBelow;
        if (below != 0) {
            var beam = renderer;
            renderer = (pose, consumer) -> {
                drawingBelow = below;
                try {
                    beam.render(pose, consumer);
                } finally {
                    drawingBelow = 0;
                }
            };
        }
        original.call(collector, poses, type, renderer);
    }

    @Inject(method = "renderPart", at = @At("HEAD"), cancellable = true)
    private static void gradientglass$fade(PoseStack.Pose pose, VertexConsumer consumer, int color, int bottom, int top,
                                           float x1, float z1, float x2, float z2, float x3, float z3, float x4, float z4,
                                           float u0, float u1, float topV, float bottomV, CallbackInfo ci) {
        int below = drawingBelow;
        if (below == 0 || top <= bottom) return;
        ci.cancel();
        int lower = color & 0xff000000 | below & 0xffffff;
        int end = bottom + 1;
        float endV = bottomV + (topV - bottomV) / (top - bottom);
        gradientglass$face(pose, consumer, lower, color, bottom, end, x1, z1, x2, z2, u0, u1, bottomV, endV);
        gradientglass$face(pose, consumer, lower, color, bottom, end, x4, z4, x3, z3, u0, u1, bottomV, endV);
        gradientglass$face(pose, consumer, lower, color, bottom, end, x2, z2, x4, z4, u0, u1, bottomV, endV);
        gradientglass$face(pose, consumer, lower, color, bottom, end, x3, z3, x1, z1, u0, u1, bottomV, endV);
        if (end == top) return;
        drawingBelow = 0;
        try {
            renderPart(pose, consumer, color, end, top, x1, z1, x2, z2, x3, z3, x4, z4, u0, u1, topV, endV);
        } finally {
            drawingBelow = below;
        }
    }

    @Unique
    private static void gradientglass$face(PoseStack.Pose pose, VertexConsumer consumer, int lower, int upper,
                                           int bottom, int top, float x1, float z1, float x2, float z2,
                                           float u0, float u1, float bottomV, float topV) {
        addVertex(pose, consumer, upper, top, x1, z1, u1, topV);
        addVertex(pose, consumer, lower, bottom, x1, z1, u1, bottomV);
        addVertex(pose, consumer, lower, bottom, x2, z2, u0, bottomV);
        addVertex(pose, consumer, upper, top, x2, z2, u0, topV);
    }

    @Shadow
    private static void renderPart(PoseStack.Pose pose, VertexConsumer consumer, int color, int bottom, int top,
                                   float x1, float z1, float x2, float z2, float x3, float z3, float x4, float z4,
                                   float u0, float u1, float topV, float bottomV) {
        throw new AssertionError();
    }

    @Shadow
    private static void addVertex(PoseStack.Pose pose, VertexConsumer consumer, int color, int y,
                                  float x, float z, float u, float v) {
        throw new AssertionError();
    }
}
