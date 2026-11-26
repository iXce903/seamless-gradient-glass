package com.ixce.gradientglass;

import java.util.function.Predicate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.model.BlockStateModel;
import net.fabricmc.fabric.api.client.rendering.v1.AtlasSourceRegistry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

public class GradientGlass implements ClientModInitializer {
    static final Identifier[] CLEAR_TEXTURES = new Identifier[16];
    static final Identifier[] TEXTURES = new Identifier[16];
    private static final Direction[] FACES = Direction.values();
    private static final float[] SRGB_TO_LINEAR = new float[256];
    private static final int[] LINEAR_TO_SRGB = new int[4097];

    static {
        for (int mask = 0; mask < TEXTURES.length; mask++) {
            CLEAR_TEXTURES[mask] = Identifier.of("gradientglass", "block/clear_" + mask);
            TEXTURES[mask] = Identifier.of("gradientglass", "block/glass_" + mask);
        }
        for (int i = 0; i < SRGB_TO_LINEAR.length; i++) {
            double s = i / 255.0;
            SRGB_TO_LINEAR[i] = (float) (s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4));
        }
        for (int i = 0; i < LINEAR_TO_SRGB.length; i++) {
            double l = i / 4096.0;
            double s = l <= 0.0031308 ? 12.92 * l : 1.055 * Math.pow(l, 1.0 / 2.4) - 0.055;
            LINEAR_TO_SRGB[i] = Math.min(255, Math.max(0, (int) Math.round(s * 255.0)));
        }
    }

    @Override
    public void onInitializeClient() {
        AtlasSourceRegistry.register(Identifier.of("gradientglass", "clear_glass"), GlassCtmAtlas.CODEC);
        ModelLoadingPlugin.register(context -> {
            var surfaces = new GlassSurface.Cache();
            var event = context.modifyBlockModelAfterBake();
            var phase = Identifier.of("gradientglass", "glass_faces");
            event.addPhaseOrdering(ModelModifier.WRAP_LAST_PHASE, phase);
            event.register(phase, (model, ctx) -> {
                if (GradientPaneModel.color(ctx.state()) != 0) return new GradientPaneModel(model, surfaces);
                return color(ctx.state()) == 0 ? model : new GlassModel(model, surfaces);
            });
        });
    }

    private static int color(BlockState state) {
        if (state.isOf(Blocks.TINTED_GLASS)) return 0xff272528;
        if (state.isOf(Blocks.GLASS) || state.isOf(Blocks.BEACON)) return 0x55ffffff;
        if (state.getBlock() instanceof StainedGlassBlock glass) {
            return 0xff000000 | glass.getColor().getEntityColor();
        }
        return 0;
    }

    // A zero sample denotes a non-glass neighbor and contributes no weight.
    // RGB channels are averaged in linear light (IEC 61966-2-1) to avoid dark/muddy midtones.
    static int average(int center, int a, int b, int diagonal) {
        if ((a == 0 || a == center) && (b == 0 || b == center) && (diagonal == 0 || diagonal == center)) {
            return center;
        }
        int count = 1;
        int alpha = center >>> 24;
        float r = SRGB_TO_LINEAR[(center >>> 16) & 255];
        float g = SRGB_TO_LINEAR[(center >>> 8) & 255];
        float bl = SRGB_TO_LINEAR[center & 255];
        if (a != 0) {
            count++;
            alpha += a >>> 24;
            r += SRGB_TO_LINEAR[(a >>> 16) & 255];
            g += SRGB_TO_LINEAR[(a >>> 8) & 255];
            bl += SRGB_TO_LINEAR[a & 255];
        }
        if (b != 0) {
            count++;
            alpha += b >>> 24;
            r += SRGB_TO_LINEAR[(b >>> 16) & 255];
            g += SRGB_TO_LINEAR[(b >>> 8) & 255];
            bl += SRGB_TO_LINEAR[b & 255];
        }
        if (diagonal != 0) {
            count++;
            alpha += diagonal >>> 24;
            r += SRGB_TO_LINEAR[(diagonal >>> 16) & 255];
            g += SRGB_TO_LINEAR[(diagonal >>> 8) & 255];
            bl += SRGB_TO_LINEAR[diagonal & 255];
        }
        float scale = 4096f / count;
        return (alpha / count << 24)
                | (LINEAR_TO_SRGB[Math.min(4096, Math.round(r * scale))] << 16)
                | (LINEAR_TO_SRGB[Math.min(4096, Math.round(g * scale))] << 8)
                | LINEAR_TO_SRGB[Math.min(4096, Math.round(bl * scale))];
    }

    private static final class GlassModel extends WrapperBlockStateModel {
        private final GlassSurface.Cache surfaces;
        private static final ThreadLocal<Samples> SAMPLES = ThreadLocal.withInitial(Samples::new);

        private static final class Samples implements QuadTransform {
            int shellFaces;

            // The vanilla beacon shell occupies the full cube. The core and base
            // are inset. Keep those baked quads (and their resource-pack textures).
            @Override
            public boolean transform(MutableQuadView quad) {
                Direction face = quad.lightFace();
                int corners = 0;
                for (int i = 0; i < 4; i++) {
                    float x = quad.x(i), y = quad.y(i), z = quad.z(i);
                    if ((x != 0 && x != 1) || (y != 0 && y != 1) || (z != 0 && z != 1)) return true;
                    double depth = face.getAxis().choose((double) x, y, z);
                    if (depth != (face.getDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0)) return true;
                    int corner = switch (face.getAxis()) {
                        case X -> (int) (2 * y + z);
                        case Y -> (int) (2 * x + z);
                        case Z -> (int) (2 * x + y);
                    };
                    corners |= 1 << corner;
                }
                if (corners != 15) return true;
                shellFaces |= 1 << face.ordinal();
                return false;
            }

            final BlockPos.Mutable cursor = new BlockPos.Mutable();
            final int[] neighbors = new int[27];
            final int[] grid = new int[9];
            int sampled;

            BlockState stateAt(BlockRenderView view, BlockPos pos, int dx, int dy, int dz) {
                cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                return view.getBlockState(cursor);
            }

            int get(BlockRenderView view, BlockPos pos, int dx, int dy, int dz) {
                int index = 13 + dx + 3 * dy + 9 * dz;
                int bit = 1 << index;
                if ((sampled & bit) == 0) {
                    neighbors[index] = color(stateAt(view, pos, dx, dy, dz));
                    sampled |= bit;
                }
                return neighbors[index];
            }

            int get(BlockRenderView view, BlockPos pos, Direction dir, int sign) {
                return get(view, pos, dir.getOffsetX() * sign, dir.getOffsetY() * sign, dir.getOffsetZ() * sign);
            }

            int getOffset(BlockRenderView view, BlockPos pos, int offset) {
                int index = 13 + offset;
                if ((sampled & (1 << index)) != 0) return neighbors[index];
                return get(view, pos, index % 3 - 1, index / 3 % 3 - 1, index / 9 - 1);
            }

            private BlockState stateAtOffset(BlockRenderView view, BlockPos pos, int offset) {
                int index = 13 + offset;
                return stateAt(view, pos, index % 3 - 1, index / 3 % 3 - 1, index / 9 - 1);
            }

            boolean showsFace(BlockRenderView view, BlockPos pos, int block, int open, Direction face) {
                if (getOffset(view, pos, block) == 0) return false;
                BlockState outside = stateAtOffset(view, pos, open);
                return outside.isAir() || Block.shouldDrawSide(stateAtOffset(view, pos, block), outside, face);
            }

            boolean hasAdjacentFold(BlockRenderView view, BlockPos pos, int normal, int along, int across,
                                    Direction face, Direction foldedFace) {
                int outside = normal + along;
                return getOffset(view, pos, outside) == 0
                        && showsFace(view, pos, along, outside, face)
                        && showsFace(view, pos, outside + across, outside, foldedFace);
            }

            int foldCorner(BlockRenderView view, BlockPos pos, int normal, int h, int v) {
                // Each choice of side along the three axes addresses one distinct block.
                int count = 0, first = 0, alpha = 0;
                boolean uniform = true;
                float red = 0f, green = 0f, blue = 0f;
                for (int depth = 0; depth <= 1; depth++) {
                    for (int y = 0; y <= 1; y++) {
                        for (int x = 0; x <= 1; x++) {
                            int sample = getOffset(view, pos, depth * normal + x * h + y * v);
                            if (sample == 0) continue;
                            if (count == 0) first = sample;
                            else if (sample != first) uniform = false;
                            count++;
                            alpha += sample >>> 24;
                            red += SRGB_TO_LINEAR[(sample >>> 16) & 255];
                            green += SRGB_TO_LINEAR[(sample >>> 8) & 255];
                            blue += SRGB_TO_LINEAR[sample & 255];
                        }
                    }
                }
                if (uniform) return first;
                float scale = 4096f / count;
                return (alpha / count << 24)
                        | (LINEAR_TO_SRGB[Math.min(4096, Math.round(red * scale))] << 16)
                        | (LINEAR_TO_SRGB[Math.min(4096, Math.round(green * scale))] << 8)
                        | LINEAR_TO_SRGB[Math.min(4096, Math.round(blue * scale))];
            }
        }

        GlassModel(BlockStateModel model, GlassSurface.Cache surfaces) {
            super(model);
            this.surfaces = surfaces;
        }

        @Override
        public Object createGeometryKey(BlockRenderView view, BlockPos pos, BlockState state, Random random) {
            return null; // Each face depends on the surrounding glass.
        }

        @Override
        public void emitQuads(QuadEmitter out, BlockRenderView view, BlockPos pos, BlockState state,
                              Random random, Predicate<Direction> cullTest) {
            int center = color(state);
            var textures = surfaces.get();
            var samples = SAMPLES.get();
            int faces = (1 << FACES.length) - 1;
            if (state.isOf(Blocks.BEACON)) {
                samples.shellFaces = 0;
                out.pushTransform(samples);
                try {
                    super.emitQuads(out, view, pos, state, random, cullTest);
                } finally {
                    out.popTransform();
                }
                faces = samples.shellFaces;
            }
            samples.neighbors[13] = center;
            samples.sampled = 1 << 13;
            int[] grid = samples.grid;
            for (Direction face : FACES) {
                if ((faces & (1 << face.ordinal())) == 0 || cullTest.test(face) || samples.get(view, pos, face, 1) != 0) continue;
                // Local axes match QuadEmitter.square on all six cube faces.
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
                int mask = 0;
                int diagonals = 0;
                int normal = offset(face), horizontal = offset(right), vertical = offset(up);
                // A diagonal glass block across a visible face's edge presents
                // a perpendicular face toward the same open block.
                int folds = (samples.showsFace(view, pos, normal - horizontal, normal, right) ? 1 : 0)
                        | (samples.showsFace(view, pos, normal + horizontal, normal, right.getOpposite()) ? 2 : 0)
                        | (samples.showsFace(view, pos, normal - vertical, normal, up) ? 4 : 0)
                        | (samples.showsFace(view, pos, normal + vertical, normal, up.getOpposite()) ? 8 : 0);
                grid[4] = center;
                for (int sign : SIGNS) {
                    int h = samples.get(view, pos, right, sign);
                    int v = samples.get(view, pos, up, sign);
                    int hBit = sign < 0 ? 1 : 2, vBit = sign < 0 ? 4 : 8;
                    int hOffset = horizontal * sign, vOffset = vertical * sign;
                    grid[4 + sign] = average(center, h,
                            (folds & hBit) != 0 ? samples.getOffset(view, pos, normal + hOffset) : 0, 0);
                    grid[4 + 3 * sign] = average(center, v,
                            (folds & vBit) != 0 ? samples.getOffset(view, pos, normal + vOffset) : 0, 0);
                    if (h == 0) mask |= hBit;
                    if (v == 0) mask |= vBit;
                    for (int dy : SIGNS) {
                        int side = samples.get(view, pos, up, dy);
                        int diagonal = h == 0 && side == 0 ? 0 :
                            samples.get(view, pos, right.getOffsetX() * sign + up.getOffsetX() * dy,
                                    right.getOffsetY() * sign + up.getOffsetY() * dy,
                                    right.getOffsetZ() * sign + up.getOffsetZ() * dy);
                        int cornerV = vertical * dy;
                        int cornerEdges = hBit | (dy < 0 ? 4 : 8);
                        int cornerFolds = folds & cornerEdges;
                        boolean continues = false;
                        if (cornerFolds != cornerEdges && (h != 0 || side != 0)
                                && samples.getOffset(view, pos, normal + hOffset + cornerV) != 0) {
                            continues = (h != 0 && samples.hasAdjacentFold(view, pos, normal, hOffset, cornerV,
                                    face, dy < 0 ? up : up.getOpposite()))
                                    || (side != 0 && samples.hasAdjacentFold(view, pos, normal, cornerV, hOffset,
                                    face, sign < 0 ? right : right.getOpposite()));
                        }
                        // At a fold, the corner joins through the visible surface.
                        // Two perpendicular folds meet across the same open block.
                        boolean connected = cornerFolds == 0 ? diagonal != 0
                                : cornerFolds == cornerEdges || continues;
                        if (connected) diagonals |= dy < 0 ? (sign < 0 ? 2 : 8) : (sign < 0 ? 128 : 32);
                        grid[4 + sign + 3 * dy] = cornerFolds != 0 || continues
                                ? samples.foldCorner(view, pos, normal, hOffset, cornerV)
                                : average(center, h, side, diagonal);
                    }
                }
                GlassSurface surface = textures.blocks[GlassCtmAtlas.tile(mask & ~folds, diagonals)];
                // The face uses one sprite per layer. Equal ARGB along an axis also
                // guarantees equal alpha and tint on both CTM layers along that axis.
                int width = grid[0] == grid[1] && grid[1] == grid[2]
                        && grid[3] == grid[4] && grid[4] == grid[5]
                        && grid[6] == grid[7] && grid[7] == grid[8] ? 2 : 1;
                int height = grid[0] == grid[3] && grid[3] == grid[6]
                        && grid[1] == grid[4] && grid[4] == grid[7]
                        && grid[2] == grid[5] && grid[5] == grid[8] ? 2 : 1;
                for (int y = 0; y < 2; y += height) {
                    for (int x = 0; x < 2; x += width) {
                        int i = 3 * y + x, top = i + 3 * height;
                        float l = x * .5f, b = y * .5f, r = (x + width) * .5f, t = (y + height) * .5f;
                        int alpha = (grid[top] & grid[i] & grid[i + width] & grid[top + width]) >>> 24;
                        for (int layer = alpha == 255 || surface.clear() == null ? 1 : 0; layer <= 1; layer++) {
                            int c0 = surface.tint(grid[top], layer), c1 = surface.tint(grid[i], layer);
                            int c2 = surface.tint(grid[i + width], layer), c3 = surface.tint(grid[top + width], layer);
                            if (((c0 | c1 | c2 | c3) >>> 24) == 0) continue;
                            out.square(face, l, b, r, t, 0);
                            out.uv(0, l, 1-t).uv(1, l, 1-b).uv(2, r, 1-b).uv(3, r, 1-t);
                            out.spriteBake(surface.sprite(layer), QuadEmitter.BAKE_NORMALIZED);
                            out.color(c0, c1, c2, c3);
                            out.renderLayer(BlockRenderLayer.TRANSLUCENT).emit();
                        }
                    }
                }
            }
        }

        private static int offset(Direction direction) {
            return direction.getOffsetX() + 3 * direction.getOffsetY() + 9 * direction.getOffsetZ();
        }

        private static final int[] SIGNS = {-1, 1};
    }
}



