package com.ixce.gradientglass.model;

import com.ixce.gradientglass.GlassColor;
import com.ixce.gradientglass.GlassMaterial;
import com.ixce.gradientglass.config.GlassConfig;
import com.ixce.gradientglass.texture.GlassTexture;
import com.ixce.gradientglass.texture.GlassTextures;
import java.util.Arrays;
import java.util.function.Predicate;
//? if >=1.21.5 {
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
//?} else {
/*import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.resources.model.BakedModel;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

//? if >=1.21.5 {
public final class GlassPaneModel extends WrapperBlockStateModel {
//?} else {
/*public final class GlassPaneModel extends ForwardingBakedModel {
*///?}
    private static final float LO = 7f / 16, HI = 9f / 16;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final Direction[] VERTICAL = {Direction.UP, Direction.DOWN};
    private static final ThreadLocal<Field> FIELD = ThreadLocal.withInitial(Field::new);
    private final GlassTextures.Lazy textures;

    //? if >=1.21.5 {
    public GlassPaneModel(BlockStateModel model, GlassTextures.Lazy textures) {
        super(model);
        this.textures = textures;
    }
    //?} else {
    /*public GlassPaneModel(BakedModel model, GlassTextures.Lazy textures) {
        this.wrapped = model;
        this.textures = textures;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }
    *///?}

    private static boolean hasPart(BlockState state, Direction arm) {
        if (GlassMaterial.ofPane(state) == GlassMaterial.NONE) return false;
        return arm == null || GlassNeighborhood.hasArm(state, arm);
    }

    //? if >=1.21.5 {
    @Override
    public Object createGeometryKey(BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random) {
        return null;
    }

    @Override
    public void emitQuads(QuadEmitter out, BlockAndTintGetter view, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        var textures = this.textures.get();
        var field = FIELD.get();
        field.begin(view, pos, textures);
        field.prepare(state);
        emitPart(out, textures, field, state, null, cullTest);
        for (Direction arm : HORIZONTAL) {
            if (hasPart(state, arm)) emitPart(out, textures, field, state, arm, cullTest);
        }
    }
    //?} else {
    /*@Override
    public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos,
                               Supplier<RandomSource> randomSupplier, RenderContext context) {
        var textures = this.textures.get();
        var field = FIELD.get();
        field.begin(view, pos, textures);
        field.prepare(state);
        QuadEmitter out = context.getEmitter();
        Predicate<Direction> cullTest = context::isFaceCulled;
        emitPart(out, textures, field, state, null, cullTest);
        for (Direction arm : HORIZONTAL) {
            if (hasPart(state, arm)) emitPart(out, textures, field, state, arm, cullTest);
        }
    }
    *///?}

    private static void emitPart(QuadEmitter out, GlassTextures textures, Field field,
                                 BlockState state, Direction arm, Predicate<Direction> cullTest) {
        float x0 = arm == Direction.WEST ? 0 : arm == Direction.EAST ? HI : LO;
        float x1 = arm == Direction.EAST ? 1 : arm == Direction.WEST ? LO : HI;
        float z0 = arm == Direction.NORTH ? 0 : arm == Direction.SOUTH ? HI : LO;
        float z1 = arm == Direction.SOUTH ? 1 : arm == Direction.NORTH ? LO : HI;
        BlockState above = field.state(0, 1, 0), below = field.state(0, -1, 0);
        boolean top = hasPart(above, arm), bottom = hasPart(below, arm);
        boolean joined = arm != null && hasPart(field.state(arm, 0), arm.getOpposite());
        int material = field.material(0, 0, 0);
        int edges = (bottom ? 0 : 4) | (top ? 0 : 8);
        field.select(arm);
        boolean fading = field.fade.reaches(0, 0, 0, 1, 1, 1);
        // Near the border: clear glass underneath, a stained haze on top.
        int stained = fading && material == GlassMaterial.CLEAR ? field.fade.nearestMaterial(0.5f, 0.5f, 0.5f) : material;
        GlassTexture side = textures.pane(stained, edges), edge = textures.paneTop(stained);
        var config = GlassConfig.current;
        var layers = field.layers;
        if (!fading) layers.select(field.patterns, material, config.patternLayers, 0, 0, 0, 1, 1, 1);
        // Bottom first: clear glass under a stained haze, or this pane's pattern under the others'.
        var sides = field.sides;
        var ends = field.edges;
        int count;
        if (fading) {
            count = 2;
            sides[0] = textures.pane(GlassMaterial.CLEAR, edges);
            ends[0] = textures.paneTop(GlassMaterial.CLEAR);
            sides[1] = side;
            ends[1] = edge;
        } else {
            count = layers.count;
            for (int k = 0; k < count; k++) {
                sides[k] = textures.pane(layers.materials[k], edges);
                ends[k] = textures.paneTop(layers.materials[k]);
                layers.alphas[k] = sides[k].alpha();
            }
        }
        boolean layered = count > 1;
        Layer kind = fading ? Layer.FADE : layered ? Layer.PATTERN : Layer.ONLY;
        int rows = field.flatUp && !layered ? 1 : config.paneRows;
        int segments = arm == null || field.flatAlong && !layered ? 1 : config.paneSegments;
        for (Direction face : HORIZONTAL) {
            if (arm == null ? hasPart(state, face) : face == arm.getOpposite()) continue;
            if (face == arm && (joined || cullTest.test(face))) continue;
            float left, right, depth;
            switch (face) {
                case SOUTH -> { left = x0; right = x1; depth = 1 - z1; }
                case NORTH -> { left = 1 - x1; right = 1 - x0; depth = z0; }
                case WEST -> { left = z0; right = z1; depth = x0; }
                case EAST -> { left = 1 - z1; right = 1 - z0; depth = 1 - x1; }
                default -> throw new AssertionError(face);
            }
            boolean endFace = face == arm || (arm == null && !hasPart(state, face.getClockWise())
                    && !hasPart(state, face.getCounterClockWise()));
            int pieces = arm != null && face.getAxis() != arm.getAxis() ? segments : 1;
            for (int row = 0; row < rows; row++) {
                float b = (float) row / rows, t = (float) (row + 1) / rows;
                for (int piece = 0; piece < pieces; piece++) {
                    float l = left + (right - left) * piece / pieces, r = left + (right - left) * (piece + 1) / pieces;
                    for (int k = 0; k < count; k++) {
                        emitSide(out, field, face, l, b, r, t, depth + GlassFade.LAYER_GAP * (count - 1 - k), endFace ? ends[k] : sides[k], kind, k);
                    }
                }
            }
        }
        for (Direction face : VERTICAL) {
            boolean covered = face == Direction.UP ? top : bottom;
            BlockState next = face == Direction.UP ? above : below;
            if (covered || (GlassMaterial.ofPane(next) == GlassMaterial.NONE && cullTest.test(face))) continue;
            for (int k = 0; k < count; k++) {
                emitCap(out, field, face, x0, x1, z0, z1, segments, GlassFade.LAYER_GAP * (count - 1 - k), ends[k], kind, k);
            }
        }
    }

    private enum Layer { ONLY, FADE, PATTERN }

    private static void emitSide(QuadEmitter out, Field field, Direction face, float l, float b, float r, float t,
                                 float depth, GlassTexture texture, Layer layer, int index) {
        out.square(face, l, b, r, t, depth);
        out.uv(0, l, 1 - t).uv(1, l, 1 - b).uv(2, r, 1 - b).uv(3, r, 1 - t);
        out.spriteBake(texture.sprite(), MutableQuadView.BAKE_NORMALIZED);
        paint(out, field, texture, layer, index);
    }

    private static void emitCap(QuadEmitter out, Field field, Direction face, float x0, float x1, float z0, float z1,
                                int segments, float depth, GlassTexture texture, Layer layer, int index) {
        boolean alongX = field.arm == Direction.EAST || field.arm == Direction.WEST;
        for (int piece = 0; piece < segments; piece++) {
            float xa = x0, xb = x1, za = z0, zb = z1;
            if (alongX) {
                xa = x0 + (x1 - x0) * piece / segments;
                xb = x0 + (x1 - x0) * (piece + 1) / segments;
            } else {
                za = z0 + (z1 - z0) * piece / segments;
                zb = z0 + (z1 - z0) * (piece + 1) / segments;
            }
            if (face == Direction.UP) out.square(Direction.UP, xa, 1 - zb, xb, 1 - za, depth);
            else out.square(Direction.DOWN, xa, za, xb, zb, depth);
            out.cullFace(null);
            for (int i = 0; i < 4; i++) out.uv(i, alongX ? out.z(i) : out.x(i), alongX ? out.x(i) : out.z(i));
            out.spriteBake(texture.sprite(), MutableQuadView.BAKE_NORMALIZED);
            paint(out, field, texture, layer, index);
        }
    }

    // index counts layers from the bottom.
    private static void paint(QuadEmitter out, Field field, GlassTexture texture, Layer layer, int index) {
        int alpha = 0;
        for (int i = 0; i < 4; i++) {
            float x = out.x(i), y = out.y(i), z = out.z(i);
            float opacity = switch (layer) {
                case ONLY -> 1;
                case FADE -> {
                    float d = field.fade.distance(x, y, z);
                    if (index == 1) yield field.fade.stained(field.clear ? -d : d);
                    yield field.clear ? 1 : field.fade.clearUnder(d);
                }
                case PATTERN -> {
                    field.layers.opacities(field.patterns, x, y, z);
                    yield field.layers.opacity[index];
                }
            };
            int color = texture.shade(field.colorAt(x, y, z), opacity);
            alpha |= color >>> 24;
            out.color(i, color);
        }
        //? if >=1.21.5 {
        if (alpha != 0) out.renderLayer(ChunkSectionLayer.TRANSLUCENT).emit();
        //?} else {
        /*if (alpha != 0) {
            var mat = GlassBlockModel.translucentMaterial();
            if (mat != null) out.material(mat);
            out.emit();
        }
        *///?}
    }

    private static final class Field {
        final GlassNeighborhood around = new GlassNeighborhood();
        final GlassFade fade = around.fade, patterns = around.patterns;
        final PatternLayers layers = new PatternLayers();
        final GlassTexture[] sides = new GlassTexture[PatternLayers.MAX_OTHERS + 1], edges = new GlassTexture[sides.length];
        boolean clear;
        private final float[] lab = new float[3];
        private final GlassColor.Mix mix = new GlassColor.Mix();
        private final float[][] post = new float[3][4], junction = new float[3][4], control = new float[3][4];
        private final float[][][] ends = new float[HORIZONTAL.length][3][4];
        // [column along the arm][row]
        private final float[][][] net = new float[3][][];
        private final int[] packed = new int[9];
        boolean flatAlong, flatUp;
        Direction arm;
        private final float[] ws = new float[3], wv = new float[3];

        void begin(BlockAndTintGetter view, BlockPos pos, GlassTextures textures) {
            around.begin(view, pos, textures);
            clear = material(0, 0, 0) == GlassMaterial.CLEAR;
        }

        BlockState state(int dx, int dy, int dz) {
            return around.state(dx, dy, dz);
        }

        BlockState state(Direction side, int dy) {
            return side == null ? state(0, dy, 0) : state(side.getStepX(), dy, side.getStepZ());
        }

        int material(int dx, int dy, int dz) {
            return around.material(dx, dy, dz);
        }

        float[] blurred(Direction side, int dy) {
            return side == null ? around.blurred(0, dy, 0) : around.blurred(side.getStepX(), dy, side.getStepZ());
        }

        private boolean joined(Direction side, int dy, Direction direction) {
            return side == null ? around.joined(0, dy, 0, direction) : around.joined(side.getStepX(), dy, side.getStepZ(), direction);
        }

        void prepare(BlockState state) {
            BlockState above = state(0, 1, 0), below = state(0, -1, 0);
            float[] self = blurred(null, 0);
            float[] up = joined(null, 0, Direction.UP) ? blurred(null, 1) : null;
            float[] down = joined(null, 0, Direction.DOWN) ? blurred(null, -1) : null;
            mix.clear().add(self).add(down).into(post[0]);
            mix.clear().add(self).into(post[1]);
            mix.clear().add(self).add(up).into(post[2]);
            for (int k = 0; k < HORIZONTAL.length; k++) {
                Direction side = HORIZONTAL[k];
                if (!hasPart(state, side)) continue;
                Direction back = side.getOpposite();
                boolean top = hasPart(above, side), bottom = hasPart(below, side);
                // The neighbor an arm reaches may be a pane or a glass block.
                float[] next = joined(null, 0, side) ? blurred(side, 0) : null;
                float[] nextUp = (top && joined(null, 1, side)) || (next != null && joined(side, 0, Direction.UP)) ? blurred(side, 1) : null;
                float[] nextDown = (bottom && joined(null, -1, side)) || (next != null && joined(side, 0, Direction.DOWN)) ? blurred(side, -1) : null;
                mix.clear().add(self).add(bottom ? down : null).add(next).add(nextDown).into(ends[k][0]);
                mix.clear().add(self).add(next).into(ends[k][1]);
                mix.clear().add(self).add(top ? up : null).add(next).add(nextUp).into(ends[k][2]);
            }
            // An edge row only counts arms the pane across that edge also has, so stacked panes
            // with different arms meet at the same color.
            for (int row = 0; row < 3; row++) {
                BlockState across = row == 2 ? above : row == 0 ? below : state;
                mix.clear();
                for (int axis = 0; axis < 4; axis += 2) {
                    boolean negative = hasPart(state, HORIZONTAL[axis]) && hasPart(across, HORIZONTAL[axis]);
                    boolean positive = hasPart(state, HORIZONTAL[axis + 1]) && hasPart(across, HORIZONTAL[axis + 1]);
                    if (!negative && !positive) continue;
                    mix.add(negative ? ends[axis][row] : post[row]).add(post[row], 2).add(positive ? ends[axis + 1][row] : post[row]);
                }
                if (mix.empty()) System.arraycopy(post[row], 0, junction[row], 0, 4);
                else mix.into(junction[row]);
            }
        }

        void select(Direction arm) {
            this.arm = arm;
            net[0] = junction;
            if (arm == null) {
                net[1] = net[2] = junction;
            } else {
                float[][] end = ends[switch (arm) {
                    case NORTH -> 0;
                    case SOUTH -> 1;
                    case WEST -> 2;
                    default -> 3;
                }];
                for (int row = 0; row < 3; row++) mix.clear().add(post[row]).add(end[row]).into(control[row]);
                net[1] = control;
                net[2] = end;
            }
            for (int column = 0; column < 3; column++) {
                for (int row = 0; row < 3; row++) packed[3 * column + row] = GlassColor.argb(net[column][row]);
            }
            flatAlong = true;
            flatUp = true;
            for (int k = 0; k < 3; k++) {
                flatAlong &= packed[k] == packed[3 + k] && packed[3 + k] == packed[6 + k];
                flatUp &= packed[3 * k] == packed[3 * k + 1] && packed[3 * k + 1] == packed[3 * k + 2];
            }
        }

        private float along(float x, float z) {
            if (arm == null) return 0;
            float t = switch (arm) {
                case EAST -> (x - HI) / (1 - HI);
                case WEST -> (LO - x) / LO;
                case SOUTH -> (z - HI) / (1 - HI);
                default -> (LO - z) / LO;
            };
            return Math.min(1, Math.max(0, t));
        }

        float[] colorAt(float x, float y, float z) {
            GlassColor.bernstein(along(x, z), ws);
            GlassColor.bernstein(y, wv);
            Arrays.fill(lab, 0);
            for (int column = 0; column < 3; column++) {
                for (int row = 0; row < 3; row++) {
                    float w = ws[column] * wv[row];
                    float[] c = net[column][row];
                    for (int k = 0; k < 3; k++) lab[k] += w * c[k];
                }
            }
            return lab;
        }
    }
}
