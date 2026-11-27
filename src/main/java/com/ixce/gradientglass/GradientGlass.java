package com.ixce.gradientglass;

import com.ixce.gradientglass.config.GlassConfig;
import com.ixce.gradientglass.model.GlassBlockModel;
import com.ixce.gradientglass.model.GlassPaneModel;
import com.ixce.gradientglass.texture.GlassSprites;
import com.ixce.gradientglass.texture.GlassTextures;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.SpriteSourceRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

public class GradientGlass implements ClientModInitializer {
    // Blur radius plus face sampling radius.
    public static final int REACH = 2;

    public static boolean isGlass(BlockState state) {
        return GlassMaterial.of(state) != GlassMaterial.NONE || GlassMaterial.ofPane(state) != GlassMaterial.NONE;
    }

    public static boolean isGradientModel(Object model) {
        return model instanceof GlassBlockModel || model instanceof GlassPaneModel;
    }

    // Vanilla rebuilds only the sections within one block of a change; glass gradients reach further.
    public static void rebuildAround(ClientLevel level, BlockPos pos) {
        level.setSectionRangeDirty(
                SectionPos.blockToSectionCoord(pos.getX() - REACH),
                SectionPos.blockToSectionCoord(pos.getY() - REACH),
                SectionPos.blockToSectionCoord(pos.getZ() - REACH),
                SectionPos.blockToSectionCoord(pos.getX() + REACH),
                SectionPos.blockToSectionCoord(pos.getY() + REACH),
                SectionPos.blockToSectionCoord(pos.getZ() + REACH));
    }

    @Override
    public void onInitializeClient() {
        GlassConfig.load();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BeaconBeamDyes.clear());
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
