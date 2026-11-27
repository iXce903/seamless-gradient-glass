package com.ixce.gradientglass;

import java.util.Arrays;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;

final class GlassPaneModel extends WrapperBlockStateModel {
    // Where the color varies, arms are split into this many pieces along their length, and panes into this many rows.
    private static final int SEGMENTS = 2, ROWS = 4;
    private static final float LO = 7f / 16, HI = 9f / 16;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final ThreadLocal<Field> FIELD = ThreadLocal.withInitial(Field::new);
    private final GlassTextures.Lazy textures;

    GlassPaneModel(BlockStateModel model, GlassTextures.Lazy textures) {
        super(model);
        this.textures = textures;
    }

    private static boolean hasPart(BlockState state, Direction arm) {
        if (GlassMaterial.ofPane(state) == GlassMaterial.NONE) return false;
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
        var textures = this.textures.get();
        var field = FIELD.get();
        field.begin(view, pos, textures);
        field.prepare(state);
        emitPart(out, textures, field, state, null, cullTest);
        for (Direction arm : HORIZONTAL) {
            if (hasPart(state, arm)) emitPart(out, textures, field, state, arm, cullTest);
        }
    }

    private static void emitPart(QuadEmitter out, GlassTextures textures, Field field,
                                 BlockState state, Direction arm, Predicate<Direction> cullTest) {
        float x0 = arm == Direction.WEST ? 0 : arm == Direction.EAST ? HI : LO;
        float x1 = arm == Direction.EAST ? 1 : arm == Direction.WEST ? LO : HI;
        float z0 = arm == Direction.NORTH ? 0 : arm == Direction.SOUTH ? HI : LO;
        float z1 = arm == Direction.SOUTH ? 1 : arm == Direction.NORTH ? LO : HI;
        BlockState above = field.state(0, 1, 0), below = field.state(0, -1, 0);
        boolean top = hasPart(above, arm), bottom = hasPart(below, arm);
        boolean joined = arm != null && hasPart(field.state(arm, 0), arm.getOpposite());
        int material = GlassMaterial.ofPane(state);
        var side = textures.pane(material, (bottom ? 0 : 4) | (top ? 0 : 8));
        var edge = textures.paneTop(material);
        field.select(arm);
        boolean clear = side.clear();
        if (!clear && !field.visible()) return;
        int rows = clear || field.flatUp ? 1 : ROWS;
        int segments = clear || arm == null || field.flatAlong ? 1 : SEGMENTS;
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
            var texture = endFace ? edge : side;
            int pieces = arm != null && face.getAxis() != arm.getAxis() ? segments : 1;
            for (int row = 0; row < rows; row++) {
                float b = (float) row / rows, t = (float) (row + 1) / rows;
                for (int piece = 0; piece < pieces; piece++) {
                    float l = left + (right - left) * piece / pieces, r = left + (right - left) * (piece + 1) / pieces;
                    out.square(face, l, b, r, t, depth);
                    out.uv(0, l, 1 - t).uv(1, l, 1 - b).uv(2, r, 1 - b).uv(3, r, 1 - t);
                    out.materialBake(texture.material(), QuadEmitter.BAKE_NORMALIZED);
                    paint(out, field, texture);
                }
            }
        }
        if (!top && (GlassMaterial.ofPane(above) != GlassMaterial.NONE || !cullTest.test(Direction.UP))) {
            emitCap(out, field, Direction.UP, x0, x1, z0, z1, segments, edge);
        }
        if (!bottom && (GlassMaterial.ofPane(below) != GlassMaterial.NONE || !cullTest.test(Direction.DOWN))) {
            emitCap(out, field, Direction.DOWN, x0, x1, z0, z1, segments, edge);
        }
    }

    private static void emitCap(QuadEmitter out, Field field, Direction face, float x0, float x1, float z0, float z1,
                                int segments, GlassTexture texture) {
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
            if (face == Direction.UP) out.square(Direction.UP, xa, 1 - zb, xb, 1 - za, 0);
            else out.square(Direction.DOWN, xa, za, xb, zb, 0);
            out.cullFace(null);
            for (int i = 0; i < 4; i++) out.uv(i, alongX ? out.z(i) : out.x(i), alongX ? out.x(i) : out.z(i));
            out.materialBake(texture.material(), QuadEmitter.BAKE_NORMALIZED);
            paint(out, field, texture);
        }
    }

    private static void paint(QuadEmitter out, Field field, GlassTexture texture) {
        for (int i = 0; i < 4; i++) out.color(i, texture.tint(field.colorAt(out.x(i), out.y(i), out.z(i))));
        out.chunkLayer(ChunkSectionLayer.TRANSLUCENT).emit();
    }

    private static final class Field {
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private BlockAndTintGetter view;
        private BlockPos pos;
        private GlassTextures textures;
        private final BlockState[] states = new BlockState[125];
        private final float[][] blurred = new float[125][4];
        private final float[][] blurredRef = new float[125][];
        private final int[] stateStamp = new int[125], blurredStamp = new int[125];
        private int stamp;
        private final GlassColor.Mix blur = new GlassColor.Mix(), mix = new GlassColor.Mix();
        private final float[][] post = new float[3][4], junction = new float[3][4], control = new float[3][4];
        private final float[][][] ends = new float[HORIZONTAL.length][3][4];
        // [column along the arm][row]
        private final float[][][] net = new float[3][][];
        private final int[] packed = new int[9];
        boolean flatAlong, flatUp;
        Direction arm;
        private final float[] ws = new float[3], wv = new float[3];

        void begin(BlockAndTintGetter view, BlockPos pos, GlassTextures textures) {
            this.view = view;
            this.pos = pos;
            this.textures = textures;
            if (++stamp == 0) {
                Arrays.fill(stateStamp, 0);
                Arrays.fill(blurredStamp, 0);
                stamp = 1;
            }
        }

        private static int index(int dx, int dy, int dz) {
            return (dx + 2) + 5 * (dy + 2) + 25 * (dz + 2);
        }

        BlockState state(int dx, int dy, int dz) {
            int index = index(dx, dy, dz);
            if (stateStamp[index] != stamp) {
                states[index] = view.getBlockState(cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
                stateStamp[index] = stamp;
            }
            return states[index];
        }

        BlockState state(Direction side, int dy) {
            return side == null ? state(0, dy, 0) : state(side.getStepX(), dy, side.getStepZ());
        }

        private float[] stainedColor(int dx, int dy, int dz) {
            int material = GlassMaterial.ofPane(state(dx, dy, dz));
            return material > GlassMaterial.CLEAR ? textures.color(material) : null;
        }

        float[] blurred(int dx, int dy, int dz) {
            int index = index(dx, dy, dz);
            if (blurredStamp[index] == stamp) return blurredRef[index];
            BlockState self = state(dx, dy, dz);
            int material = GlassMaterial.ofPane(self);
            float[] result = null;
            if (material != GlassMaterial.NONE) {
                blur.clear();
                for (int ey = -1; ey <= 1; ey++) {
                    int w = ey == 0 ? 2 : 1;
                    blur.add(stainedColor(dx, dy + ey, dz), 2 * w);
                    for (Direction side : HORIZONTAL) {
                        int nx = dx + side.getStepX(), nz = dz + side.getStepZ();
                        if (hasPart(self, side) && hasPart(state(nx, dy, nz), side.getOpposite())) {
                            blur.add(stainedColor(nx, dy + ey, nz), w);
                        }
                    }
                }
                result = blurred[index];
                if (blur.empty()) System.arraycopy(GlassColor.TRANSPARENT, 0, result, 0, 4);
                else blur.into(result);
                if (material == GlassMaterial.CLEAR) result[3] = 0;
            }
            blurredStamp[index] = stamp;
            blurredRef[index] = result;
            return result;
        }

        float[] blurred(Direction side, int dy) {
            return side == null ? blurred(0, dy, 0) : blurred(side.getStepX(), dy, side.getStepZ());
        }

        void prepare(BlockState state) {
            BlockState above = state(0, 1, 0), below = state(0, -1, 0);
            float[] self = blurred(0, 0, 0);
            float[] up = blurred(0, 1, 0), down = blurred(0, -1, 0);
            mix.clear().add(self).add(down).into(post[0]);
            System.arraycopy(self, 0, post[1], 0, 4);
            mix.clear().add(self).add(up).into(post[2]);
            for (int k = 0; k < HORIZONTAL.length; k++) {
                Direction side = HORIZONTAL[k];
                if (!hasPart(state, side)) continue;
                Direction back = side.getOpposite();
                boolean top = hasPart(above, side), bottom = hasPart(below, side);
                float[] next = hasPart(state(side, 0), back) ? blurred(side, 0) : null;
                float[] nextUp = (top || next != null) && hasPart(state(side, 1), back) ? blurred(side, 1) : null;
                float[] nextDown = (bottom || next != null) && hasPart(state(side, -1), back) ? blurred(side, -1) : null;
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

        boolean visible() {
            for (int color : packed) if ((color >>> 24) != 0) return true;
            return false;
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

        int colorAt(float x, float y, float z) {
            GlassColor.bernstein(along(x, z), ws);
            GlassColor.bernstein(y, wv);
            float lightness = 0, a = 0, b = 0, alpha = 0;
            for (int column = 0; column < 3; column++) {
                for (int row = 0; row < 3; row++) {
                    float w = ws[column] * wv[row];
                    float[] c = net[column][row];
                    lightness += w * c[0];
                    a += w * c[1];
                    b += w * c[2];
                    alpha += w * c[3];
                }
            }
            return GlassColor.argb(lightness, a, b, alpha);
        }
    }
}
