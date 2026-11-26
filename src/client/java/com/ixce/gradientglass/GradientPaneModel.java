package com.ixce.gradientglass;

import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

final class GradientPaneModel extends WrapperBlockStateModel {
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private static final class Scratch {
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final int[] inner = new int[3], outer = new int[3];
    }

    private final GlassSurface.Cache surfaces;
    private static final float LO = 7f / 16, HI = 9f / 16;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    GradientPaneModel(BlockStateModel model, GlassSurface.Cache surfaces) {
        super(model);
        this.surfaces = surfaces;
    }

    static int color(BlockState state) {
        if (state.is(Blocks.GLASS_PANE)) return 0x55ffffff;
        if (state.getBlock() instanceof StainedGlassPaneBlock pane) return 0xff000000 | pane.getColor().getTextureDiffuseColor();
        return 0;
    }

    private static boolean hasPart(BlockState state, Direction arm) {
        if (color(state) == 0) return false;
        if (arm == null) return true;
        return switch (arm) {
            case NORTH -> state.getValue(CrossCollisionBlock.NORTH);
            case SOUTH -> state.getValue(CrossCollisionBlock.SOUTH);
            case WEST -> state.getValue(CrossCollisionBlock.WEST);
            case EAST -> state.getValue(CrossCollisionBlock.EAST);
            default -> false;
        };
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random) {
        return null;
    }

    @Override
    public void emitQuads(QuadEmitter out, BlockAndTintGetter view, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        var textures = surfaces.get();
        var scratch = SCRATCH.get();
        var above = view.getBlockState(scratch.cursor.set(pos).move(Direction.UP));
        var below = view.getBlockState(scratch.cursor.set(pos).move(Direction.DOWN));
        int center = color(state);
        scratch.inner[0] = GradientGlass.average(center, color(below), 0, 0);
        scratch.inner[1] = center;
        scratch.inner[2] = GradientGlass.average(center, color(above), 0, 0);
        emitPart(out, textures, scratch, view, pos, state, above, below, null, cullTest);
        for (Direction arm : HORIZONTAL) {
            if (hasPart(state, arm)) emitPart(out, textures, scratch, view, pos, state, above, below, arm, cullTest);
        }
    }

    private static void emitPart(QuadEmitter out, GlassSurface.Textures textures, Scratch scratch, BlockAndTintGetter view, BlockPos pos,
            BlockState state, BlockState above, BlockState below, Direction arm, Predicate<Direction> cullTest) {
        float x0 = arm == Direction.WEST ? 0 : arm == Direction.EAST ? HI : LO;
        float x1 = arm == Direction.EAST ? 1 : arm == Direction.WEST ? LO : HI;
        float z0 = arm == Direction.NORTH ? 0 : arm == Direction.SOUTH ? HI : LO;
        float z1 = arm == Direction.SOUTH ? 1 : arm == Direction.NORTH ? LO : HI;
        boolean top = hasPart(above, arm), bottom = hasPart(below, arm);
        int center = scratch.inner[1];
        int up = top ? color(above) : 0, down = bottom ? color(below) : 0;
        int side = 0, sideUp = 0, sideDown = 0;
        if (arm != null) {
            var next = scratch.cursor.set(pos).move(arm);
            Direction back = arm.getOpposite();
            BlockState neighbor = view.getBlockState(next);
            if (hasPart(neighbor, back)) side = color(neighbor);
            if (top || side != 0) {
                var upper = view.getBlockState(next.setY(pos.getY() + 1));
                if (hasPart(upper, back)) sideUp = color(upper);
            }
            if (bottom || side != 0) {
                var lower = view.getBlockState(next.setY(pos.getY() - 1));
                if (hasPart(lower, back)) sideDown = color(lower);
            }
        }
        int[] inner = scratch.inner, outer = scratch.outer;
        outer[0] = GradientGlass.average(center, down, side, sideDown);
        outer[1] = GradientGlass.average(center, side, 0, 0);
        outer[2] = GradientGlass.average(center, up, side, sideUp);
        int mask = (bottom ? 0 : 4) | (top ? 0 : 8);
        var surface = textures.panes[mask];
        var cap = surface.clear() == null ? textures.panes[0] : textures.cap;
        for (int layer = surface.clear() == null ? 1 : 0; layer <= 1; layer++) {
            if (!visible(inner, outer, surface, layer)) continue;
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
                for (int row = 0; row < 2; row++) {
                    float b = row * .5f, t = b + .5f;
                    out.square(face, left, b, right, t, depth);
                    out.uv(0, left, 1 - t).uv(1, left, 1 - b).uv(2, right, 1 - b).uv(3, right, 1 - t);
                    boolean endFace = face == arm || (arm == null && !hasPart(state, face.getClockWise())
                            && !hasPart(state, face.getCounterClockWise()));
                    var material = surface.clear() != null && endFace ? cap : surface;
                    out.materialBake(material.material(layer), QuadEmitter.BAKE_NORMALIZED);
                    paint(out, inner, outer, surface, layer);
                }
            }
            if (!top && (color(above) != 0 || !cullTest.test(Direction.UP))) {
                out.square(Direction.UP, x0, 1 - z1, x1, 1 - z0, 0).cullFace(null);
                capTexture(out, cap.material(layer), arm);
                paint(out, inner, outer, surface, layer);
            }
            if (!bottom && (color(below) != 0 || !cullTest.test(Direction.DOWN))) {
                out.square(Direction.DOWN, x0, z0, x1, z1, 0).cullFace(null);
                capTexture(out, cap.material(layer), arm);
                paint(out, inner, outer, surface, layer);
            }
        }
    }

    private static void capTexture(QuadEmitter out, Material.Baked material, Direction arm) {
        boolean alongX = arm == Direction.EAST || arm == Direction.WEST;
        for (int i = 0; i < 4; i++) {
            out.uv(i, alongX ? out.z(i) : out.x(i), alongX ? out.x(i) : out.z(i));
        }
        out.materialBake(material, QuadEmitter.BAKE_NORMALIZED);
    }

    private static boolean visible(int[] inner, int[] outer, GlassSurface surface, int layer) {
        for (int i = 0; i < inner.length; i++) {
            if (((surface.tint(inner[i], layer) | surface.tint(outer[i], layer)) >>> 24) != 0) return true;
        }
        return false;
    }

    private static void paint(QuadEmitter out, int[] inner, int[] outer, GlassSurface surface, int layer) {
        for (int i = 0; i < 4; i++) {
            float x = out.x(i), z = out.z(i);
            int[] column = x == 0 || x == 1 || z == 0 || z == 1 ? outer : inner;
            out.color(i, surface.tint(column[Math.round(out.y(i) * 2)], layer));
        }
        out.chunkLayer(ChunkSectionLayer.TRANSLUCENT).emit();
    }
}
