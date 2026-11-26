package com.ixce.gradientglass;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

final class GradientPaneModel extends ForwardingBakedModel {
    private static final float LO = 7f / 16, HI = 9f / 16;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private static final class Scratch {
        final int[] inner = new int[3];
        final int[] outer = new int[3];
    }

    GradientPaneModel(BakedModel model) {
        this.wrapped = model;
    }

    static int color(BlockState state) {
        if (state.isOf(Blocks.GLASS_PANE)) return GradientGlass.CLEAR_COLOR;
        if (state.getBlock() instanceof StainedGlassPaneBlock pane) return GradientGlass.dyeArgb(pane.getColor());
        return 0;
    }

    private static boolean hasPart(BlockState state, Direction arm) {
        if (color(state) == 0) return false;
        if (arm == null) return true;
        return state.get(switch (arm) {
            case NORTH -> PaneBlock.NORTH;
            case SOUTH -> PaneBlock.SOUTH;
            case WEST -> PaneBlock.WEST;
            case EAST -> PaneBlock.EAST;
            default -> throw new AssertionError(arm);
        });
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView view, BlockState state, BlockPos pos,
                               Supplier<Random> randomSupplier, RenderContext context) {
        if (color(state) == 0) {
            super.emitBlockQuads(view, state, pos, randomSupplier, context);
            return;
        }
        boolean clear = state.isOf(Blocks.GLASS_PANE);
        QuadEmitter out = context.getEmitter();
        Predicate<Direction> cullTest = context::isFaceCulled;
        var atlas = MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        RenderMaterial material = GlassSurface.usesCutout(atlas, true, clear)
                ? GradientGlass.cutoutMaterial()
                : GradientGlass.translucentMaterial();
        var above = view.getBlockState(pos.up());
        var below = view.getBlockState(pos.down());
        Scratch scratch = SCRATCH.get();
        emitPart(out, atlas, material, scratch, view, pos, state, above, below, null, clear, cullTest);
        for (Direction arm : HORIZONTAL) {
            if (hasPart(state, arm)) {
                emitPart(out, atlas, material, scratch, view, pos, state, above, below, arm, clear, cullTest);
            }
        }
    }

    // A vanilla pane is a 2/16-wide post plus one rectangular arm per connection.
    private static void emitPart(QuadEmitter out, SpriteAtlasTexture atlas, RenderMaterial renderMaterial,
            Scratch scratch, BlockRenderView view, BlockPos pos, BlockState state, BlockState above,
            BlockState below, Direction arm, boolean clear, Predicate<Direction> cullTest) {
        float x0 = arm == Direction.WEST ? 0 : arm == Direction.EAST ? HI : LO;
        float x1 = arm == Direction.EAST ? 1 : arm == Direction.WEST ? LO : HI;
        float z0 = arm == Direction.NORTH ? 0 : arm == Direction.SOUTH ? HI : LO;
        float z1 = arm == Direction.SOUTH ? 1 : arm == Direction.NORTH ? LO : HI;
        boolean top = hasPart(above, arm), bottom = hasPart(below, arm);
        int center = color(state);
        int up = top ? color(above) : 0, down = bottom ? color(below) : 0;
        int side = 0, sideUp = 0, sideDown = 0;
        if (arm != null) {
            BlockPos next = pos.offset(arm);
            Direction back = arm.getOpposite();
            BlockState neighbor = view.getBlockState(next);
            if (hasPart(neighbor, back)) side = color(neighbor);
            var upper = view.getBlockState(next.up());
            var lower = view.getBlockState(next.down());
            if (hasPart(upper, back)) sideUp = color(upper);
            if (hasPart(lower, back)) sideDown = color(lower);
        }

        int[] inner = scratch.inner;
        int[] outer = scratch.outer;
        inner[0] = GradientGlass.average(center, color(below), 0, 0);
        inner[1] = center;
        inner[2] = GradientGlass.average(center, color(above), 0, 0);
        outer[0] = GradientGlass.average(center, down, side, sideDown);
        outer[1] = GradientGlass.average(center, side, 0, 0);
        outer[2] = GradientGlass.average(center, up, side, sideUp);

        int mask = (bottom ? 0 : 4) | (top ? 0 : 8);
        Sprite surfaceSprite = GlassSurface.sprite(atlas, mask, 255, true, clear);
        Sprite capSprite = GlassSurface.paneCap(atlas, true, clear);
        boolean verticalUniform = inner[0] == inner[1] && inner[1] == inner[2]
                && outer[0] == outer[1] && outer[1] == outer[2];

        for (Direction face : HORIZONTAL) {
            if (arm == null ? hasPart(state, face) : face == arm.getOpposite()) continue;
            if (face == arm && (side != 0 || cullTest.test(face))) continue;
            float left, right, depth;
            switch (face) {
                case SOUTH -> { left = x0; right = x1; depth = 1 - z1; }
                case NORTH -> { left = 1 - x1; right = 1 - x0; depth = z0; }
                case WEST -> { left = z0; right = z1; depth = x0; }
                case EAST -> { left = 1 - z1; right = 1 - z0; depth = 1 - x1; }
                default -> throw new AssertionError(face);
            }
            boolean endFace = face == arm || (arm == null && !hasPart(state, face.rotateYClockwise())
                    && !hasPart(state, face.rotateYCounterclockwise()));
            Sprite faceSprite = endFace ? capSprite : surfaceSprite;

            if (verticalUniform) {
                out.square(face, left, 0f, right, 1f, depth);
                out.uv(0, left, 0f).uv(1, left, 1f).uv(2, right, 1f).uv(3, right, 0f);
                out.spriteBake(faceSprite, QuadEmitter.BAKE_NORMALIZED);
                paint(out, inner, outer, renderMaterial);
            } else {
                for (int row = 0; row < 2; row++) {
                    float b = row * .5f, t = b + .5f;
                    out.square(face, left, b, right, t, depth);
                    out.uv(0, left, 1f - t).uv(1, left, 1f - b).uv(2, right, 1f - b).uv(3, right, 1f - t);
                    out.spriteBake(faceSprite, QuadEmitter.BAKE_NORMALIZED);
                    paint(out, inner, outer, renderMaterial);
                }
            }
        }
        if (!top && (color(above) != 0 || !cullTest.test(Direction.UP))) {
            out.square(Direction.UP, x0, 1 - z1, x1, 1 - z0, 0).cullFace(null);
            capTexture(out, capSprite, arm);
            paint(out, inner, outer, renderMaterial);
        }
        if (!bottom && (color(below) != 0 || !cullTest.test(Direction.DOWN))) {
            out.square(Direction.DOWN, x0, z0, x1, z1, 0).cullFace(null);
            capTexture(out, capSprite, arm);
            paint(out, inner, outer, renderMaterial);
        }
    }

    private static void capTexture(QuadEmitter out, Sprite sprite, Direction arm) {
        boolean alongX = arm == Direction.EAST || arm == Direction.WEST;
        for (int i = 0; i < 4; i++) {
            out.uv(i, alongX ? out.z(i) : out.x(i), alongX ? out.x(i) : out.z(i));
        }
        out.spriteBake(sprite, QuadEmitter.BAKE_NORMALIZED);
    }

    private static void paint(QuadEmitter out, int[] inner, int[] outer, RenderMaterial renderMaterial) {
        for (int i = 0; i < 4; i++) {
            float x = out.x(i), z = out.z(i);
            int[] column = x == 0 || x == 1 || z == 0 || z == 1 ? outer : inner;
            out.color(i, column[Math.round(out.y(i) * 2)]);
        }
        out.material(renderMaterial);
        out.emit();
    }
}
