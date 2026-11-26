package com.ixce.gradientglass;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

// Carry the lower segment color through the vanilla beam renderer for this draw only.
public record BeamTransition(VertexConsumerProvider delegate, float[] lowerColor) implements VertexConsumerProvider {
    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return delegate.getBuffer(layer);
    }
}
