package com.ixce.gradientglass.model;

import com.ixce.gradientglass.GlassColor;
import com.ixce.gradientglass.GlassMaterial;
import com.ixce.gradientglass.config.GlassConfig;
import com.ixce.gradientglass.texture.Ctm;
import com.ixce.gradientglass.texture.GlassTexture;
import com.ixce.gradientglass.texture.GlassTextures;
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

public final class GlassBlockModel extends WrapperBlockStateModel {
    private static final Direction[] FACES = Direction.values();
    private static final int[] SIGNS = {-1, 1};
    private static final ThreadLocal<Sampler> SAMPLER = ThreadLocal.withInitial(Sampler::new);
    private final GlassTextures.Lazy textures;

    public GlassBlockModel(BlockStateModel model, GlassTextures.Lazy textures) {
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
        sampler.begin(view, pos, textures.get());
        int material = sampler.get(0, 0, 0);
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
        int tile = Ctm.tile(edges & ~folds, diagonals);
        int subdivisions = GlassConfig.current.blockSubdivisions;
        var textures = sampler.textures;
        boolean clear = material == GlassMaterial.CLEAR;
        int[] net = sampler.net, upper = sampler.upper, lower = sampler.lower;
        for (int k = 0; k < 9; k++) net[k] = GlassColor.argb(grid[k]);
        boolean flatH = net[0] == net[1] && net[1] == net[2] && net[3] == net[4] && net[4] == net[5]
                && net[6] == net[7] && net[7] == net[8];
        boolean flatV = net[0] == net[3] && net[3] == net[6] && net[1] == net[4] && net[4] == net[7]
                && net[2] == net[5] && net[5] == net[8];
        // The face runs from origin along right (u) and up (v), in block coordinates.
        float ox = 0.5f + 0.5f * (face.getStepX() - right.getStepX() - up.getStepX());
        float oy = 0.5f + 0.5f * (face.getStepY() - right.getStepY() - up.getStepY());
        float oz = 0.5f + 0.5f * (face.getStepZ() - right.getStepZ() - up.getStepZ());
        int rx = right.getStepX(), ry = right.getStepY(), rz = right.getStepZ();
        int ux = up.getStepX(), uy = up.getStepY(), uz = up.getStepZ();
        var fade = sampler.around.fade;
        boolean fading = fade.reaches(Math.min(ox, ox + rx + ux), Math.min(oy, oy + ry + uy), Math.min(oz, oz + rz + uz),
                Math.max(ox, ox + rx + ux), Math.max(oy, oy + ry + uy), Math.max(oz, oz + rz + uz));
        float[] lab = sampler.lab;
        var own = textures.block(material, tile);
        float x0 = Math.min(ox, ox + rx + ux), y0 = Math.min(oy, oy + ry + uy), z0 = Math.min(oz, oz + rz + uz);
        float x1 = Math.max(ox, ox + rx + ux), y1 = Math.max(oy, oy + ry + uy), z1 = Math.max(oz, oz + rz + uz);
        var layers = sampler.layers;
        if (!fading) layers.select(sampler.around.patterns, material, GlassConfig.current.patternLayers, x0, y0, z0, x1, y1, z1);
        boolean blending = !fading && layers.count > 1;
        if (!fading && !blending) {
            int nx = flatH ? 1 : subdivisions, ny = flatV ? 1 : subdivisions;
            for (int y = 0; y <= ny; y++) {
                for (int x = 0; x <= nx; x++) {
                    sampler.patch((float) x / nx, (float) y / ny, lab);
                    upper[y * (nx + 1) + x] = own.shade(lab, 1);
                }
            }
            emitLayer(out, face, own, upper, nx, ny, 0);
            return;
        }
        if (!fading) {
            emitPatternBlend(out, sampler, face, tile, subdivisions, ox, oy, oz, rx, ry, rz, ux, uy, uz);
            return;
        }
        int n = subdivisions;
        var stained = clear ? textures.block(fade.nearestMaterial(ox + 0.5f * (rx + ux), oy + 0.5f * (ry + uy), oz + 0.5f * (rz + uz)), tile) : own;
        var underneath = textures.block(GlassMaterial.CLEAR, tile);
        for (int y = 0; y <= n; y++) {
            for (int x = 0; x <= n; x++) {
                float u = (float) x / n, v = (float) y / n;
                sampler.patch(u, v, lab);
                float d = fade.distance(ox + u * rx + v * ux, oy + u * ry + v * uy, oz + u * rz + v * uz);
                int i = y * (n + 1) + x;
                upper[i] = stained.shade(lab, fade.stained(clear ? -d : d));
                lower[i] = underneath.shade(lab, clear ? 1 : fade.clearUnder(d));
            }
        }
        emitLayer(out, face, underneath, lower, n, n, GlassFade.LAYER_GAP);
        emitLayer(out, face, stained, upper, n, n, 0);
    }

    private static void emitPatternBlend(QuadEmitter out, Sampler sampler, Direction face, int tile, int n,
                                         float ox, float oy, float oz, int rx, int ry, int rz, int ux, int uy, int uz) {
        var patterns = sampler.around.patterns;
        var layers = sampler.layers;
        var textures = sampler.layerTextures;
        for (int k = 0; k < layers.count; k++) {
            textures[k] = sampler.textures.block(layers.materials[k], tile);
            layers.alphas[k] = textures[k].alpha();
        }
        float[] lab = sampler.lab;
        for (int y = 0; y <= n; y++) {
            for (int x = 0; x <= n; x++) {
                float u = (float) x / n, v = (float) y / n;
                sampler.patch(u, v, lab);
                layers.opacities(patterns, ox + u * rx + v * ux, oy + u * ry + v * uy, oz + u * rz + v * uz);
                for (int k = 0; k < layers.count; k++) sampler.layerColors[k][y * (n + 1) + x] = textures[k].shade(lab, layers.opacity[k]);
            }
        }
        for (int k = 0; k < layers.count; k++) {
            emitLayer(out, face, textures[k], sampler.layerColors[k], n, n, GlassFade.LAYER_GAP * (layers.count - 1 - k));
        }
    }

    private static void emitLayer(QuadEmitter out, Direction face, GlassTexture texture, int[] vertex, int nx, int ny, float depth) {
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
                emitQuad(out, face, texture, l, b, r, t, depth, flip, c0, c1, c2, c3);
            }
        }
    }

    private static void emitQuad(QuadEmitter out, Direction face, GlassTexture texture, float l, float b, float r, float t,
                                 float depth, boolean flip, int c0, int c1, int c2, int c3) {
        out.square(face, l, b, r, t, depth);
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
        final GlassNeighborhood around = new GlassNeighborhood();
        final float[] lab = new float[3];
        GlassTextures textures;
        private final GlassColor.Mix mix = new GlassColor.Mix();
        final float[][] grid = new float[9][4];
        final int[] net = new int[9];
        final int[] upper = new int[(GlassConfig.MAX_SUBDIVISIONS + 1) * (GlassConfig.MAX_SUBDIVISIONS + 1)], lower = new int[upper.length];
        final PatternLayers layers = new PatternLayers();
        final GlassTexture[] layerTextures = new GlassTexture[PatternLayers.MAX_OTHERS + 1];
        final int[][] layerColors = new int[PatternLayers.MAX_OTHERS + 1][upper.length];
        private final float[] wu = new float[3], wv = new float[3];

        void begin(BlockAndTintGetter view, BlockPos pos, GlassTextures textures) {
            this.textures = textures;
            around.begin(view, pos, textures);
        }

        /** The material of the glass block at an offset; panes count as NONE here. */
        int get(int dx, int dy, int dz) {
            return around.block(dx, dy, dz);
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
            return around.state(index % 3 - 1, index / 3 % 3 - 1, index / 9 - 1);
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

        private float[] blurred(int offset) {
            int index = 13 + offset;
            int dx = index % 3 - 1, dy = index / 3 % 3 - 1, dz = index / 9 - 1;
            return get(dx, dy, dz) != GlassMaterial.NONE ? around.blurred(dx, dy, dz) : null;
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

        void patch(float u, float v, float[] out) {
            GlassColor.bernstein(u, wu);
            GlassColor.bernstein(v, wv);
            Arrays.fill(out, 0);
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    float w = wu[x] * wv[y];
                    float[] c = grid[3 * y + x];
                    for (int k = 0; k < 3; k++) out[k] += w * c[k];
                }
            }
        }
    }
}
