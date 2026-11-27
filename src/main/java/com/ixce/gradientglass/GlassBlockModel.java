package com.ixce.gradientglass;

import java.util.Arrays;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class GlassBlockModel extends WrapperBlockStateModel {
    // A face whose color varies along an axis is split into this many quads along it.
    private static final int SUBDIVISIONS = 4;
    private static final Direction[] FACES = Direction.values();
    private static final int[] SIGNS = {-1, 1};
    private static final ThreadLocal<Sampler> SAMPLER = ThreadLocal.withInitial(Sampler::new);
    private final GlassTextures.Lazy textures;

    GlassBlockModel(BlockStateModel model, GlassTextures.Lazy textures) {
        super(model);
        this.textures = textures;
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random) {
        return null;
    }

    @Override
    public void emitQuads(QuadEmitter out, BlockAndTintGetter view, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        var sampler = SAMPLER.get();
        int faces = (1 << FACES.length) - 1;
        if (state.is(Blocks.BEACON)) {
            // The beacon keeps its inner model; only its glass shell is replaced.
            var shell = sampler.shell;
            shell.faces = 0;
            out.pushTransform(shell);
            try {
                super.emitQuads(out, view, pos, state, random, cullTest);
            } finally {
                out.popTransform();
            }
            faces = shell.faces;
        }
        int material = GlassMaterial.of(state);
        sampler.begin(view, pos, material, textures.get());
        for (Direction face : FACES) {
            if ((faces & (1 << face.ordinal())) != 0 && !cullTest.test(face) && sampler.get(face, 1) == GlassMaterial.NONE) {
                emitFace(out, sampler, material, face);
            }
        }
    }

    private static void emitFace(QuadEmitter out, Sampler sampler, int material, Direction face) {
        Direction right = switch (face) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case EAST -> Direction.NORTH;
            default -> Direction.EAST;
        };
        Direction up = switch (face) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
        float[][] grid = sampler.grid;
        int none = Sampler.NO_OFFSET, glass = GlassMaterial.NONE;
        int normal = offset(face), horizontal = offset(right), vertical = offset(up);
        int folds = (sampler.showsFace(normal - horizontal, normal, right) ? 1 : 0)
                | (sampler.showsFace(normal + horizontal, normal, right.getOpposite()) ? 2 : 0)
                | (sampler.showsFace(normal - vertical, normal, up) ? 4 : 0)
                | (sampler.showsFace(normal + vertical, normal, up.getOpposite()) ? 8 : 0);
        int edges = 0, diagonals = 0;
        sampler.mean(grid[4], 0, none, none, none);
        for (int sign : SIGNS) {
            int h = sampler.get(right, sign), v = sampler.get(up, sign);
            int hBit = sign < 0 ? 1 : 2, vBit = sign < 0 ? 4 : 8;
            int hOffset = horizontal * sign, vOffset = vertical * sign;
            sampler.mean(grid[4 + sign], 0, hOffset, (folds & hBit) != 0 ? normal + hOffset : none, none);
            sampler.mean(grid[4 + 3 * sign], 0, vOffset, (folds & vBit) != 0 ? normal + vOffset : none, none);
            if (h == glass) edges |= hBit;
            if (v == glass) edges |= vBit;
            for (int dy : SIGNS) {
                int side = sampler.get(up, dy);
                int diagonal = h == glass && side == glass ? glass : sampler.get(
                        right.getStepX() * sign + up.getStepX() * dy,
                        right.getStepY() * sign + up.getStepY() * dy,
                        right.getStepZ() * sign + up.getStepZ() * dy);
                int cornerV = vertical * dy;
                int cornerEdges = hBit | (dy < 0 ? 4 : 8);
                int cornerFolds = folds & cornerEdges;
                boolean continues = false;
                if (cornerFolds != cornerEdges && (h != glass || side != glass)
                        && sampler.getOffset(normal + hOffset + cornerV) != glass) {
                    continues = (h != glass && sampler.hasAdjacentFold(normal, hOffset, cornerV, face, dy < 0 ? up : up.getOpposite()))
                            || (side != glass && sampler.hasAdjacentFold(normal, cornerV, hOffset, face, sign < 0 ? right : right.getOpposite()));
                }
                boolean connected = cornerFolds == 0 ? diagonal != glass : cornerFolds == cornerEdges || continues;
                if (connected) diagonals |= dy < 0 ? (sign < 0 ? 2 : 8) : (sign < 0 ? 128 : 32);
                float[] corner = grid[4 + sign + 3 * dy];
                if (cornerFolds != 0 || continues) sampler.foldCorner(corner, normal, hOffset, cornerV);
                else sampler.mean(corner, 0, hOffset, cornerV, h == glass && side == glass ? none : hOffset + cornerV);
            }
        }
        var texture = sampler.textures.block(material, Ctm.tile(edges & ~folds, diagonals));
        if (texture.clear()) {
            emitQuad(out, face, texture, 0, 0, 1, 1, false, -1, -1, -1, -1);
            return;
        }

        int[] net = sampler.net, vertex = sampler.vertex;
        for (int k = 0; k < 9; k++) net[k] = GlassColor.argb(grid[k]);
        boolean flatH = net[0] == net[1] && net[1] == net[2] && net[3] == net[4] && net[4] == net[5]
                && net[6] == net[7] && net[7] == net[8];
        boolean flatV = net[0] == net[3] && net[3] == net[6] && net[1] == net[4] && net[4] == net[7]
                && net[2] == net[5] && net[5] == net[8];
        int nx = flatH ? 1 : SUBDIVISIONS, ny = flatV ? 1 : SUBDIVISIONS;
        for (int y = 0; y <= ny; y++) {
            for (int x = 0; x <= nx; x++) vertex[y * (nx + 1) + x] = texture.tint(sampler.patch((float) x / nx, (float) y / ny));
        }
        for (int y = 0; y < ny; y++) {
            for (int x = 0; x < nx; x++) {
                int i = y * (nx + 1) + x, above = i + nx + 1;
                int c0 = vertex[above], c1 = vertex[i], c2 = vertex[i + 1], c3 = vertex[above + 1];
                if (((c0 | c1 | c2 | c3) >>> 24) == 0) continue;
                // Symmetric tie-break, so the triangles do not streak the gradient.
                boolean flip = false;
                if (nx > 1 && ny > 1) {
                    int d1 = distanceSquared(c0, c2), d2 = distanceSquared(c1, c3);
                    flip = d2 < d1 || (d2 == d1 && (2 * x < nx) != (2 * y < ny));
                }
                float l = (float) x / nx, b = (float) y / ny, r = (float) (x + 1) / nx, t = (float) (y + 1) / ny;
                emitQuad(out, face, texture, l, b, r, t, flip, c0, c1, c2, c3);
            }
        }
    }

    private static void emitQuad(QuadEmitter out, Direction face, GlassTexture texture,
                                 float l, float b, float r, float t, boolean flip, int c0, int c1, int c2, int c3) {
        out.square(face, l, b, r, t, 0);
        if (flip) {
            float x0 = out.x(0), y0 = out.y(0), z0 = out.z(0);
            out.pos(0, out.x(1), out.y(1), out.z(1)).pos(1, out.x(2), out.y(2), out.z(2))
                    .pos(2, out.x(3), out.y(3), out.z(3)).pos(3, x0, y0, z0);
            out.uv(0, l, 1 - b).uv(1, r, 1 - b).uv(2, r, 1 - t).uv(3, l, 1 - t);
            out.color(c1, c2, c3, c0);
        } else {
            out.uv(0, l, 1 - t).uv(1, l, 1 - b).uv(2, r, 1 - b).uv(3, r, 1 - t);
            out.color(c0, c1, c2, c3);
        }
        out.materialBake(texture.material(), QuadEmitter.BAKE_NORMALIZED);
        out.chunkLayer(ChunkSectionLayer.TRANSLUCENT).emit();
    }

    private static int offset(Direction direction) {
        return direction.getStepX() + 3 * direction.getStepY() + 9 * direction.getStepZ();
    }

    private static int distanceSquared(int a, int b) {
        int da = (a >>> 24) - (b >>> 24);
        int dr = ((a >>> 16) & 255) - ((b >>> 16) & 255);
        int dg = ((a >>> 8) & 255) - ((b >>> 8) & 255);
        int db = (a & 255) - (b & 255);
        return da * da + dr * dr + dg * dg + db * db;
    }

    private static final class ShellRemover implements QuadTransform {
        int faces;

        @Override
        public boolean transform(MutableQuadView quad) {
            Direction face = quad.lightFace();
            float depth = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
            int corners = 0;
            for (int i = 0; i < 4; i++) {
                float x = quad.x(i), y = quad.y(i), z = quad.z(i);
                if ((x != 0 && x != 1) || (y != 0 && y != 1) || (z != 0 && z != 1)) return true;
                if (face.getAxis().choose(x, y, z) != depth) return true;
                corners |= 1 << switch (face.getAxis()) {
                    case X -> (int) (2 * y + z);
                    case Y -> (int) (2 * x + z);
                    case Z -> (int) (2 * x + y);
                };
            }
            if (corners != 15) return true;
            faces |= 1 << face.ordinal();
            return false;
        }
    }

    // Offsets are dx + 3 dy + 9 dz.
    private static final class Sampler {
        static final int NO_OFFSET = Integer.MIN_VALUE;
        final ShellRemover shell = new ShellRemover();
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private BlockAndTintGetter view;
        private BlockPos pos;
        GlassTextures textures;
        private final int[] materials = new int[125];
        private final float[][] blurred = new float[27][4];
        private final float[][] blurredRef = new float[27][];
        private final int[] materialStamp = new int[125], blurredStamp = new int[27];
        private int stamp;
        private final GlassColor.Mix blur = new GlassColor.Mix(), mix = new GlassColor.Mix();
        final float[][] grid = new float[9][4];
        final int[] net = new int[9];
        final int[] vertex = new int[(SUBDIVISIONS + 1) * (SUBDIVISIONS + 1)];
        private final float[] wu = new float[3], wv = new float[3];

        void begin(BlockAndTintGetter view, BlockPos pos, int material, GlassTextures textures) {
            this.view = view;
            this.pos = pos;
            this.textures = textures;
            if (++stamp == 0) {
                Arrays.fill(materialStamp, 0);
                Arrays.fill(blurredStamp, 0);
                stamp = 1;
            }
            materials[index(0, 0, 0)] = material;
            materialStamp[index(0, 0, 0)] = stamp;
        }

        private static int index(int dx, int dy, int dz) {
            return (dx + 2) + 5 * (dy + 2) + 25 * (dz + 2);
        }

        private BlockState state(int dx, int dy, int dz) {
            return view.getBlockState(cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
        }

        int get(int dx, int dy, int dz) {
            int index = index(dx, dy, dz);
            if (materialStamp[index] != stamp) {
                materials[index] = GlassMaterial.of(state(dx, dy, dz));
                materialStamp[index] = stamp;
            }
            return materials[index];
        }

        int get(Direction direction, int sign) {
            return get(direction.getStepX() * sign, direction.getStepY() * sign, direction.getStepZ() * sign);
        }

        int getOffset(int offset) {
            int index = 13 + offset;
            return get(index % 3 - 1, index / 3 % 3 - 1, index / 9 - 1);
        }

        private BlockState stateAtOffset(int offset) {
            int index = 13 + offset;
            return state(index % 3 - 1, index / 3 % 3 - 1, index / 9 - 1);
        }

        boolean showsFace(int block, int open, Direction face) {
            if (getOffset(block) == GlassMaterial.NONE) return false;
            BlockState outside = stateAtOffset(open);
            return outside.isAir() || Block.shouldRenderFace(stateAtOffset(block), outside, face);
        }

        boolean hasAdjacentFold(int normal, int along, int across, Direction face, Direction foldedFace) {
            int outside = normal + along;
            return getOffset(outside) == GlassMaterial.NONE
                    && showsFace(along, outside, face)
                    && showsFace(outside + across, outside, foldedFace);
        }

        // The kernel is the same from every side, so faces meeting at a fold agree.
        float[] blurred(int offset) {
            int index = 13 + offset;
            if (blurredStamp[index] == stamp) return blurredRef[index];
            int dx = index % 3 - 1, dy = index / 3 % 3 - 1, dz = index / 9 - 1;
            int self = get(dx, dy, dz);
            float[] result = null;
            if (self != GlassMaterial.NONE) {
                blur.clear();
                for (int ez = -1; ez <= 1; ez++) {
                    for (int ey = -1; ey <= 1; ey++) {
                        for (int ex = -1; ex <= 1; ex++) {
                            int material = get(dx + ex, dy + ey, dz + ez);
                            if (material > GlassMaterial.CLEAR) {
                                blur.add(textures.color(material), 1 << (3 - Math.abs(ex) - Math.abs(ey) - Math.abs(ez)));
                            }
                        }
                    }
                }
                result = blurred[index];
                if (blur.empty()) System.arraycopy(GlassColor.TRANSPARENT, 0, result, 0, 4);
                else blur.into(result);
                if (self == GlassMaterial.CLEAR) result[3] = 0;
            }
            blurredStamp[index] = stamp;
            blurredRef[index] = result;
            return result;
        }

        void mean(float[] out, int a, int b, int c, int d) {
            mix.clear().add(blurred(a));
            if (b != NO_OFFSET) mix.add(blurred(b));
            if (c != NO_OFFSET) mix.add(blurred(c));
            if (d != NO_OFFSET) mix.add(blurred(d));
            mix.into(out);
        }

        void foldCorner(float[] out, int normal, int h, int v) {
            mix.clear();
            for (int depth = 0; depth <= 1; depth++) {
                for (int y = 0; y <= 1; y++) {
                    for (int x = 0; x <= 1; x++) mix.add(blurred(depth * normal + x * h + y * v));
                }
            }
            mix.into(out);
        }

        int patch(float u, float v) {
            GlassColor.bernstein(u, wu);
            GlassColor.bernstein(v, wv);
            float lightness = 0, a = 0, b = 0, alpha = 0;
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    float w = wu[x] * wv[y];
                    float[] c = grid[3 * y + x];
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
