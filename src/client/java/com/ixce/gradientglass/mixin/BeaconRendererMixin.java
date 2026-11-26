package com.ixce.gradientglass.mixin;

import com.ixce.gradientglass.BeamTransition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Arrays;
import java.util.List;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BeaconBlockEntity.BeamSegment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BeaconBlockEntityRenderer.class)
abstract class BeaconRendererMixin {
    @WrapOperation(method = "render(Lnet/minecraft/block/entity/BeaconBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
        at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/block/entity/BeaconBlockEntityRenderer;renderBeam(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;FJII[F)V"))
    private void gradientglass$segment(MatrixStack matrices, VertexConsumerProvider buffers, float tickDelta,
            long time, int y, int height, float[] color, Operation<Void> draw,
            @Local List<BeamSegment> segments, @Local(index = 11) int index) {
        if (index > 0) {
            float[] previous = segments.get(index - 1).getColor();
            if (!Arrays.equals(previous, color)) buffers = new BeamTransition(buffers, previous);
        }
        draw.call(matrices, buffers, tickDelta, time, y, height, color);
    }

    @WrapOperation(method = "renderBeam(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/util/Identifier;FFJII[FFF)V",
        at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/block/entity/BeaconBlockEntityRenderer;renderBeamLayer(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;FFFFIIFFFFFFFFFFFF)V"), require = 2)
    private static void gradientglass$layer(MatrixStack matrices, VertexConsumer vertices, float red, float green,
            float blue, float alpha, int bottom, int top, float x1, float z1, float x2, float z2,
            float x3, float z3, float x4, float z4, float u0, float u1, float topV, float bottomV,
            Operation<Void> draw, @Local(argsOnly = true) VertexConsumerProvider buffers) {
        if (buffers instanceof BeamTransition transition && top > bottom) {
            float[] lower = transition.lowerColor();
            int end = bottom + 1;
            float endV = bottomV + (topV - bottomV) / (top - bottom);
            MatrixStack.Entry entry = matrices.peek();
            Matrix4f posMat = entry.getPositionMatrix();
            Matrix3f normMat = entry.getNormalMatrix();
            gradientglass$face(posMat, normMat, vertices, bottom, end, lower[0], lower[1], lower[2], red, green, blue, alpha, x1, z1, x2, z2, u0, u1, bottomV, endV);
            gradientglass$face(posMat, normMat, vertices, bottom, end, lower[0], lower[1], lower[2], red, green, blue, alpha, x4, z4, x3, z3, u0, u1, bottomV, endV);
            gradientglass$face(posMat, normMat, vertices, bottom, end, lower[0], lower[1], lower[2], red, green, blue, alpha, x2, z2, x4, z4, u0, u1, bottomV, endV);
            gradientglass$face(posMat, normMat, vertices, bottom, end, lower[0], lower[1], lower[2], red, green, blue, alpha, x3, z3, x1, z1, u0, u1, bottomV, endV);
            if (end == top) return;
            bottom = end;
            bottomV = endV;
        }
        draw.call(matrices, vertices, red, green, blue, alpha, bottom, top, x1, z1, x2, z2, x3, z3, x4, z4, u0, u1, topV, bottomV);
    }

    @Unique
    private static void gradientglass$face(Matrix4f posMat, Matrix3f normMat, VertexConsumer vertices, int bottom,
            int top, float lr, float lg, float lb, float ur, float ug, float ub, float alpha,
            float x1, float z1, float x2, float z2, float u0, float u1, float bottomV, float topV) {
        renderBeamVertex(posMat, normMat, vertices, ur, ug, ub, alpha, top, x1, z1, u1, topV);
        renderBeamVertex(posMat, normMat, vertices, lr, lg, lb, alpha, bottom, x1, z1, u1, bottomV);
        renderBeamVertex(posMat, normMat, vertices, lr, lg, lb, alpha, bottom, x2, z2, u0, bottomV);
        renderBeamVertex(posMat, normMat, vertices, ur, ug, ub, alpha, top, x2, z2, u0, topV);
    }

    @Shadow
    private static void renderBeamVertex(Matrix4f positionMatrix, Matrix3f normalMatrix, VertexConsumer vertices,
            float red, float green, float blue, float alpha, int y, float x, float z, float u, float v) {
        throw new AssertionError();
    }
}
