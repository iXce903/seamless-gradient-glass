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
//? if >=1.21.5 {
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import com.ixce.gradientglass.mixin.SpriteSourcesAccessor;
//?} else {
/*import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
*///?}
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
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
        //? if >=1.21.2 {
        level.setSectionRangeDirty(
                SectionPos.blockToSectionCoord(pos.getX() - REACH),
                SectionPos.blockToSectionCoord(pos.getY() - REACH),
                SectionPos.blockToSectionCoord(pos.getZ() - REACH),
                SectionPos.blockToSectionCoord(pos.getX() + REACH),
                SectionPos.blockToSectionCoord(pos.getY() + REACH),
                SectionPos.blockToSectionCoord(pos.getZ() + REACH));
        //?} else {
        /*Minecraft.getInstance().levelRenderer.setBlocksDirty(
                pos.getX() - REACH, pos.getY() - REACH, pos.getZ() - REACH,
                pos.getX() + REACH, pos.getY() + REACH, pos.getZ() + REACH);
        *///?}
    }

    @Override
    public void onInitializeClient() {
        GlassConfig.load();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BeaconBeamDyes.clear());
        //? if >=1.21.5 {
        BlockRenderLayerMap.putBlocks(ChunkSectionLayer.TRANSLUCENT, Blocks.GLASS, Blocks.GLASS_PANE);
        SpriteSourcesAccessor.gradientglass$idMapper().put(ResourceLocation.fromNamespaceAndPath("gradientglass", "glass"), GlassSprites.CODEC);
        ModelLoadingPlugin.register(context -> {
            var textures = new GlassTextures.Lazy();
            var event = context.modifyBlockModelAfterBake();
            var phase = ResourceLocation.fromNamespaceAndPath("gradientglass", "glass_faces");
            event.addPhaseOrdering(ModelModifier.WRAP_LAST_PHASE, phase);
            event.register(phase, (model, ctx) -> {
                if (GlassMaterial.ofPane(ctx.state()) != GlassMaterial.NONE) return new GlassPaneModel(model, textures);
                if (GlassMaterial.of(ctx.state()) != GlassMaterial.NONE) return new GlassBlockModel(model, textures);
                return model;
            });
        });
        //?} else {
        /*BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(), Blocks.GLASS, Blocks.GLASS_PANE);
        // Trigger static registration of GlassSprites.TYPE.
        var ignored = GlassSprites.TYPE;
        ModelLoadingPlugin.register(context -> {
            var textures = new GlassTextures.Lazy();
            var event = context.modifyModelAfterBake();
            var phase = ResourceLocation.fromNamespaceAndPath("gradientglass", "glass_faces");
            event.addPhaseOrdering(ModelModifier.WRAP_LAST_PHASE, phase);
            event.register(phase, (model, ctx) -> {
                if (model == null) return null;
                var id = ctx.topLevelId();
                if (id == null || !"minecraft".equals(id.id().getNamespace())) return model;
                Block block = BuiltInRegistries.BLOCK.get(id.id());
                BlockState state = block.defaultBlockState();
                if (GlassMaterial.ofPane(state) != GlassMaterial.NONE) return new GlassPaneModel(model, textures);
                if (GlassMaterial.of(state) != GlassMaterial.NONE) return new GlassBlockModel(model, textures);
                return model;
            });
        });
        *///?}
    }
}
