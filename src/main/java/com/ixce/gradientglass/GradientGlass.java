package com.ixce.gradientglass;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.rendering.v1.SpriteSourceRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

public class GradientGlass implements ClientModInitializer {
    // Blur radius plus face sampling radius.
    public static final int REACH = 2;

    public static boolean isGlass(BlockState state) {
        return GlassMaterial.of(state) != GlassMaterial.NONE || GlassMaterial.ofPane(state) != GlassMaterial.NONE;
    }

    @Override
    public void onInitializeClient() {
        SpriteSourceRegistry.register(Identifier.fromNamespaceAndPath("gradientglass", "glass"), GlassSprites.CODEC);
        ModelLoadingPlugin.register(context -> {
            var textures = new GlassTextures.Lazy();
            var event = context.modifyBlockModelAfterBake();
            var phase = Identifier.fromNamespaceAndPath("gradientglass", "glass_faces");
            event.addPhaseOrdering(ModelModifier.WRAP_LAST_PHASE, phase);
            event.register(phase, (model, ctx) -> {
                if (GlassMaterial.ofPane(ctx.state()) != GlassMaterial.NONE) return new GlassPaneModel(model, textures);
                if (GlassMaterial.of(ctx.state()) != GlassMaterial.NONE) return new GlassBlockModel(model, textures);
                return model;
            });
        });
    }
}
