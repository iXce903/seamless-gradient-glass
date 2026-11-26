package com.ixce.gradientglass;

import com.ixce.gradientglass.mixin.AtlasSourceManagerAccessor;
import java.util.function.Supplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.registry.Registries;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

public class GradientGlass implements ClientModInitializer {
    static final Identifier[] TEXTURES = new Identifier[16];
    // Sentinel for clear glass in neighbor grids: distinct alpha byte (0x01) internally,
    // NEVER passed directly to QuadEmitter (all emitted vertex colors have alpha 0xFF).
    static final int CLEAR_SENTINEL = 0x01ffffff;
    static final int CLEAR_COLOR = 0xffffffff;
    static final int TINTED_COLOR = 0xff2d2633;
    private static final Direction[] FACES = Direction.values();
    private static final float[] SRGB_TO_LINEAR = new float[256];
    private static final int[] LINEAR_TO_SRGB = new int[4097];
    private static volatile RenderMaterial translucentMaterial;
    private static volatile RenderMaterial cutoutMaterial;

    static {
        for (int mask = 0; mask < TEXTURES.length; mask++) {
            TEXTURES[mask] = new Identifier("gradientglass", "block/glass_" + mask);
        }
        for (int i = 0; i < 256; i++) {
            double u = i / 255.0;
            SRGB_TO_LINEAR[i] = (float) (u <= 0.04045 ? u / 12.92 : Math.pow((u + 0.055) / 1.055, 2.4));
        }
        for (int i = 0; i <= 4096; i++) {
            double c = i / 4096.0;
            double u = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055;
            LINEAR_TO_SRGB[i] = Math.min(255, Math.max(0, (int) Math.round(u * 255.0)));
        }
    }

    @Override
    public void onInitializeClient() {
        AtlasSourceManagerAccessor.getSourceTypeById().putIfAbsent(
                new Identifier("gradientglass", "clear_glass"), GlassCtmAtlas.TYPE);
        ModelLoadingPlugin.register(context -> {
            var event = context.modifyModelAfterBake();
            var phase = new Identifier("gradientglass", "glass_faces");
            event.addPhaseOrdering(ModelModifier.WRAP_LAST_PHASE, phase);
            event.register(phase, (model, ctx) -> {
                if (model == null) return null;
                if (ctx.id() instanceof ModelIdentifier mid && !mid.getVariant().equals("inventory")) {
                    Block block = Registries.BLOCK.get(new Identifier(mid.getNamespace(), mid.getPath()));
                    if (block == Blocks.GLASS_PANE || block instanceof StainedGlassPaneBlock) {
                        GlassSurface.invalidate();
                        return new GradientPaneModel(model);
                    }
                    if (block == Blocks.GLASS || block == Blocks.TINTED_GLASS || block instanceof StainedGlassBlock) {
                        GlassSurface.invalidate();
                        return new GlassModel(model);
                    }
                }
                return model;
            });
        });
    }

    static RenderMaterial translucentMaterial() {
        RenderMaterial material = translucentMaterial;
        if (material == null) {
            material = RendererAccess.INSTANCE.getRenderer()
                    .materialFinder()
                    .blendMode(BlendMode.TRANSLUCENT)
                    .find();
            translucentMaterial = material;
        }
        return material;
    }

    static RenderMaterial cutoutMaterial() {
        RenderMaterial material = cutoutMaterial;
        if (material == null) {
            material = RendererAccess.INSTANCE.getRenderer()
                    .materialFinder()
                    .blendMode(BlendMode.CUTOUT)
                    .find();
            cutoutMaterial = material;
        }
        return material;
    }

    static int dyeArgb(DyeColor color) {
        float[] c = color.getColorComponents();
        return 0xff000000
                | (Math.round(c[0] * 255f) << 16)
                | (Math.round(c[1] * 255f) << 8)
                | Math.round(c[2] * 255f);
    }

    // Unified glass sampling: 0=not glass, CLEAR_SENTINEL=clear glass, 0xFFRRGGBB=stained/tinted glass.
    private static int glassSample(BlockState state) {
        if (state.isOf(Blocks.GLASS)) return CLEAR_SENTINEL;
        if (state.isOf(Blocks.TINTED_GLASS)) return GlassCtmAtlas.tintedRgb;
        if (state.getBlock() instanceof StainedGlassBlock glass) return dyeArgb(glass.getColor());
        return 0;
    }

    private static boolean isTintedOrStained(int sample) {
        return sample != 0 && sample != CLEAR_SENTINEL;
    }

    // 4-sample linear RGB average (used by GradientPaneModel and 3D edge midpoints).
    static int average(int s0, int s1, int s2, int s3) {
        return average7(s0, s1, s2, s3, 0, 0, 0);
    }

    // Up to 7 non-zero samples around a 3D lattice corner (8th cube is pos+face which is always 0).
    // Strictly symmetric across all blocks sharing the 3D corner -> mathematically zero seam!
    private static int average7(int s0, int s1, int s2, int s3, int s4, int s5, int s6) {
        int count = 0;
        float lr = 0f, lg = 0f, lb = 0f;
        int first = 0;
        boolean allSame = true;

        int[] arr = ScratchHolder.TMP7.get();
        arr[0] = s0; arr[1] = s1; arr[2] = s2; arr[3] = s3; arr[4] = s4; arr[5] = s5; arr[6] = s6;
        for (int k = 0; k < 7; k++) {
            int s = arr[k];
            if (s == 0) continue;
            int c = s == CLEAR_SENTINEL ? CLEAR_COLOR : s;
            if (count == 0) first = c;
            else if (c != first) allSame = false;
            count++;
            lr += SRGB_TO_LINEAR[(c >>> 16) & 255];
            lg += SRGB_TO_LINEAR[(c >>> 8) & 255];
            lb += SRGB_TO_LINEAR[c & 255];
        }
        if (count == 0) return CLEAR_COLOR;
        if (allSame) return first;
        float inv = 4096f / count;
        int r = LINEAR_TO_SRGB[Math.min(4096, Math.round(lr * inv))];
        int g = LINEAR_TO_SRGB[Math.min(4096, Math.round(lg * inv))];
        int bl = LINEAR_TO_SRGB[Math.min(4096, Math.round(lb * inv))];
        return 0xff000000 | (r << 16) | (g << 8) | bl;
    }

    // 4:1:1:1:1 linear RGB center smoothing: aligns center vertex with 4-block diagonal corners
    // so 45-deg color stripes form exact straight iso-contours with zero sawtooth!
    private static int smoothCenter(int center, int bL, int bR, int bD, int bU) {
        int c = center == CLEAR_SENTINEL ? CLEAR_COLOR : center;
        if (isSameOrZero(bL, center) && isSameOrZero(bR, center)
                && isSameOrZero(bD, center) && isSameOrZero(bU, center)) {
            return c;
        }
        int weight = 4;
        float lr = 4f * SRGB_TO_LINEAR[(c >>> 16) & 255];
        float lg = 4f * SRGB_TO_LINEAR[(c >>> 8) & 255];
        float lb = 4f * SRGB_TO_LINEAR[c & 255];
        int[] nb = ScratchHolder.TMP7.get();
        nb[0] = bL; nb[1] = bR; nb[2] = bD; nb[3] = bU;
        for (int k = 0; k < 4; k++) {
            int s = nb[k];
            if (s == 0) continue;
            int sc = s == CLEAR_SENTINEL ? CLEAR_COLOR : s;
            weight++;
            lr += SRGB_TO_LINEAR[(sc >>> 16) & 255];
            lg += SRGB_TO_LINEAR[(sc >>> 8) & 255];
            lb += SRGB_TO_LINEAR[sc & 255];
        }
        float inv = 4096f / weight;
        int r = LINEAR_TO_SRGB[Math.min(4096, Math.round(lr * inv))];
        int g = LINEAR_TO_SRGB[Math.min(4096, Math.round(lg * inv))];
        int bl = LINEAR_TO_SRGB[Math.min(4096, Math.round(lb * inv))];
        return 0xff000000 | (r << 16) | (g << 8) | bl;
    }

    private static int smoothCenterTintOnly(int center, int bL, int bR, int bD, int bU, int fallback) {
        if (!isTintedOrStained(center)) return fallback;
        int weight = 4;
        float lr = 4f * SRGB_TO_LINEAR[(center >>> 16) & 255];
        float lg = 4f * SRGB_TO_LINEAR[(center >>> 8) & 255];
        float lb = 4f * SRGB_TO_LINEAR[center & 255];
        boolean allSame = true;
        int[] nb = ScratchHolder.TMP7.get();
        nb[0] = bL; nb[1] = bR; nb[2] = bD; nb[3] = bU;
        for (int k = 0; k < 4; k++) {
            int s = nb[k];
            if (!isTintedOrStained(s)) continue;
            if (s != center) allSame = false;
            weight++;
            lr += SRGB_TO_LINEAR[(s >>> 16) & 255];
            lg += SRGB_TO_LINEAR[(s >>> 8) & 255];
            lb += SRGB_TO_LINEAR[s & 255];
        }
        if (allSame) return center;
        float inv = 4096f / weight;
        int r = LINEAR_TO_SRGB[Math.min(4096, Math.round(lr * inv))];
        int g = LINEAR_TO_SRGB[Math.min(4096, Math.round(lg * inv))];
        int bl = LINEAR_TO_SRGB[Math.min(4096, Math.round(lb * inv))];
        return 0xff000000 | (r << 16) | (g << 8) | bl;
    }

    private static int colorDistSq(int c1, int c2) {
        int dr = ((c1 >>> 16) & 255) - ((c2 >>> 16) & 255);
        int dg = ((c1 >>> 8) & 255) - ((c2 >>> 8) & 255);
        int db = (c1 & 255) - (c2 & 255);
        return dr * dr + dg * dg + db * db;
    }

    // Choose the triangulation diagonal with smaller color difference (aligns triangle edge to iso-contour).
    // Falls back to symmetric diamond triangulation when both diagonals have equal contrast.
    private static boolean chooseFlipDiag(int c0, int c1, int c2, int c3, int qx, int qy) {
        int d1 = colorDistSq(c0, c2);
        int d2 = colorDistSq(c1, c3);
        if (d2 != d1) return d2 < d1;
        return (qx == 0 && qy == 1) || (qx == 1 && qy == 0);
    }

    private static boolean isSameOrZero(int sample, int center) {
        return sample == 0 || sample == center;
    }

    // Average ONLY stained/tinted RGB hue over up to 7 symmetric 3D corner samples.
    private static int averageTintOnly7(int s0, int s1, int s2, int s3, int s4, int s5, int s6, int fallback) {
        int count = 0;
        float lr = 0f, lg = 0f, lb = 0f;
        int first = 0;
        boolean allSame = true;

        int[] arr = ScratchHolder.TMP7.get();
        arr[0] = s0; arr[1] = s1; arr[2] = s2; arr[3] = s3; arr[4] = s4; arr[5] = s5; arr[6] = s6;
        for (int k = 0; k < 7; k++) {
            int s = arr[k];
            if (!isTintedOrStained(s)) continue;
            if (count == 0) first = s;
            else if (s != first) allSame = false;
            count++;
            lr += SRGB_TO_LINEAR[(s >>> 16) & 255];
            lg += SRGB_TO_LINEAR[(s >>> 8) & 255];
            lb += SRGB_TO_LINEAR[s & 255];
        }
        if (count == 0) return fallback;
        if (allSame) return first;
        float inv = 4096f / count;
        int r = LINEAR_TO_SRGB[Math.min(4096, Math.round(lr * inv))];
        int g = LINEAR_TO_SRGB[Math.min(4096, Math.round(lg * inv))];
        int bl = LINEAR_TO_SRGB[Math.min(4096, Math.round(lb * inv))];
        return 0xff000000 | (r << 16) | (g << 8) | bl;
    }

    // Quantized opacity weight 0..4 (0%, 25%, 50%, 75%, 100%) over symmetric 3D neighborhood.
    private static int opacityLevel7(int s0, int s1, int s2, int s3, int s4, int s5, int s6) {
        int total = 0, stained = 0;
        if (s0 != 0) { total++; if (isTintedOrStained(s0)) stained++; }
        if (s1 != 0) { total++; if (isTintedOrStained(s1)) stained++; }
        if (s2 != 0) { total++; if (isTintedOrStained(s2)) stained++; }
        if (s3 != 0) { total++; if (isTintedOrStained(s3)) stained++; }
        if (s4 != 0) { total++; if (isTintedOrStained(s4)) stained++; }
        if (s5 != 0) { total++; if (isTintedOrStained(s5)) stained++; }
        if (s6 != 0) { total++; if (isTintedOrStained(s6)) stained++; }
        return total == 0 ? 0 : Math.round((stained * 4f) / total);
    }

    private static final class ScratchHolder {
        static final ThreadLocal<int[]> TMP7 = ThreadLocal.withInitial(() -> new int[7]);
    }

    private static final class GlassModel extends ForwardingBakedModel {
        private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

        private static final class Scratch {
            final int[] axial = new int[6];
            final int[] grid = new int[9];
            final int[] tintGrid = new int[9];
            final int[] levelGrid = new int[9];
            final BlockPos.Mutable cursor = new BlockPos.Mutable();
        }

        GlassModel(BakedModel model) {
            this.wrapped = model;
        }

        @Override
        public boolean isVanillaAdapter() {
            return false;
        }

        @Override
        public void emitBlockQuads(BlockRenderView view, BlockState state, BlockPos pos,
                                   Supplier<Random> randomSupplier, RenderContext context) {
            int center = glassSample(state);
            if (center == 0) {
                super.emitBlockQuads(view, state, pos, randomSupplier, context);
                return;
            }
            boolean clear = center == CLEAR_SENTINEL;

            Scratch scratch = SCRATCH.get();
            int[] axial = scratch.axial;
            int[] grid = scratch.grid;
            int[] tintGrid = scratch.tintGrid;
            int[] levelGrid = scratch.levelGrid;
            BlockPos.Mutable cursor = scratch.cursor;

            // Pre-sample the 6 orthogonal neighbors once per block (zero BlockPos allocations).
            for (Direction dir : FACES) {
                cursor.set(pos, dir);
                axial[dir.ordinal()] = glassSample(view.getBlockState(cursor));
            }

            QuadEmitter out = context.getEmitter();
            var atlas = MinecraftClient.getInstance().getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            RenderMaterial translucent = translucentMaterial();
            RenderMaterial clearMat = GlassSurface.usesCutout(atlas, false, true) ? cutoutMaterial() : translucent;

            for (Direction face : FACES) {
                if (axial[face.ordinal()] != 0 || context.isFaceCulled(face)) continue;

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
                Direction left = right.getOpposite();
                Direction down = up.getOpposite();

                // Back-layer (in-plane) 8 neighbors around this block
                int bL = axial[left.ordinal()];
                int bR = axial[right.ordinal()];
                int bD = axial[down.ordinal()];
                int bU = axial[up.ordinal()];
                int bBL = sample2(view, cursor, pos, left, down);
                int bBR = sample2(view, cursor, pos, right, down);
                int bTL = sample2(view, cursor, pos, left, up);
                int bTR = sample2(view, cursor, pos, right, up);

                // Front-layer (+face) 8 neighbors around pos+face (pos+face itself is known 0).
                // Sampling all 8 front neighbors symmetrically guarantees IDENTICAL colors/levels
                // at every shared 3D edge and 3D corner across all adjacent glass blocks!
                int fL = sample2(view, cursor, pos, face, left);
                int fR = sample2(view, cursor, pos, face, right);
                int fD = sample2(view, cursor, pos, face, down);
                int fU = sample2(view, cursor, pos, face, up);
                int fBL = sample3(view, cursor, pos, face, left, down);
                int fBR = sample3(view, cursor, pos, face, right, down);
                int fTL = sample3(view, cursor, pos, face, left, up);
                int fTR = sample3(view, cursor, pos, face, right, up);

                // CTM border mask: connect in-plane AND across 3D shared edges
                int mask = ((bL == 0 && fL == 0) ? 1 : 0) | ((bR == 0 && fR == 0) ? 2 : 0)
                        | ((bD == 0 && fD == 0) ? 4 : 0) | ((bU == 0 && fU == 0) ? 8 : 0);
                int diagonals = (bBL != 0 ? 2 : 0) | (bBR != 0 ? 8 : 0)
                        | (bTR != 0 ? 32 : 0) | (bTL != 0 ? 128 : 0);

                // Symmetric 3D opacity levels at all 9 grid points
                levelGrid[0] = opacityLevel7(center, bL, bD, bBL, fL, fD, fBL);
                levelGrid[1] = opacityLevel7(center, bD, fD, 0, 0, 0, 0);
                levelGrid[2] = opacityLevel7(center, bR, bD, bBR, fR, fD, fBR);
                levelGrid[3] = opacityLevel7(center, bL, fL, 0, 0, 0, 0);
                levelGrid[4] = clear ? 0 : 4;
                levelGrid[5] = opacityLevel7(center, bR, fR, 0, 0, 0, 0);
                levelGrid[6] = opacityLevel7(center, bL, bU, bTL, fL, fU, fTL);
                levelGrid[7] = opacityLevel7(center, bU, fU, 0, 0, 0, 0);
                levelGrid[8] = opacityLevel7(center, bR, bU, bTR, fR, fU, fTR);

                boolean needsFade = false;
                int expectedLevel = clear ? 0 : 4;
                for (int k = 0; k < 9; k++) {
                    if (levelGrid[k] != expectedLevel) { needsFade = true; break; }
                }

                // Case 1: Clear <-> Stained/Tinted transition face -> silky 32x32 texture-baked alpha fade!
                if (needsFade) {
                    int fallbackTint = findFirstTint(center, bL, bR, bD, bU, bBL, bBR, bTL, bTR, fL, fR, fD, fU, fBL, fBR, fTL, fTR);

                    tintGrid[0] = averageTintOnly7(center, bL, bD, bBL, fL, fD, fBL, fallbackTint);
                    tintGrid[1] = averageTintOnly7(center, bD, fD, 0, 0, 0, 0, fallbackTint);
                    tintGrid[2] = averageTintOnly7(center, bR, bD, bBR, fR, fD, fBR, fallbackTint);
                    tintGrid[3] = averageTintOnly7(center, bL, fL, 0, 0, 0, 0, fallbackTint);
                    tintGrid[4] = smoothCenterTintOnly(center, bL, bR, bD, bU, fallbackTint);
                    tintGrid[5] = averageTintOnly7(center, bR, fR, 0, 0, 0, 0, fallbackTint);
                    tintGrid[6] = averageTintOnly7(center, bL, bU, bTL, fL, fU, fTL, fallbackTint);
                    tintGrid[7] = averageTintOnly7(center, bU, fU, 0, 0, 0, 0, fallbackTint);
                    tintGrid[8] = averageTintOnly7(center, bR, bU, bTR, fR, fU, fTR, fallbackTint);

                    // Layer A: Smooth 32x32 alpha-faded tinted body (adaptive iso-contour triangulation, vertex alpha 0xFF).
                    for (int y = 0; y < 2; y++) {
                        for (int x = 0; x < 2; x++) {
                            int i = 3 * y + x;
                            int raw = GlassCtmAtlas.fadeRawIndex(levelGrid[i + 3], levelGrid[i], levelGrid[i + 1], levelGrid[i + 4]);
                            Sprite fade = GlassSurface.fadeSpriteByRaw(atlas, raw);
                            if (fade == null) continue;
                            int sym = GlassCtmAtlas.FADE_SYMMETRY[raw];
                            float l = x * .5f, b = y * .5f, r = l + .5f, t = b + .5f;
                            int c0 = tintGrid[i + 3], c1 = tintGrid[i], c2 = tintGrid[i + 1], c3 = tintGrid[i + 4];
                            boolean flipDiag = chooseFlipDiag(c0, c1, c2, c3, x, y);
                            emitSubQuad(out, face, l, b, r, t,
                                    GlassCtmAtlas.fadeU(sym, 0), GlassCtmAtlas.fadeV(sym, 0),
                                    GlassCtmAtlas.fadeU(sym, 1), GlassCtmAtlas.fadeV(sym, 1),
                                    GlassCtmAtlas.fadeU(sym, 2), GlassCtmAtlas.fadeV(sym, 2),
                                    GlassCtmAtlas.fadeU(sym, 3), GlassCtmAtlas.fadeV(sym, 3),
                                    c0, c1, c2, c3,
                                    fade, translucent, flipDiag);
                        }
                    }

                    // Layer B: Crisp border & diagonal sheen overlay (0% body alpha, matched streak alpha, alpha 0xFF).
                    Sprite frameSprite = GlassSurface.frameOverlay(atlas, mask, diagonals, false, clear);
                    RenderMaterial frameMat = clear ? clearMat : translucent;
                    grid[0] = average7(center, bL, bD, bBL, fL, fD, fBL);
                    grid[1] = average7(center, bD, fD, 0, 0, 0, 0);
                    grid[2] = average7(center, bR, bD, bBR, fR, fD, fBR);
                    grid[3] = average7(center, bL, fL, 0, 0, 0, 0);
                    grid[4] = smoothCenter(center, bL, bR, bD, bU);
                    grid[5] = average7(center, bR, fR, 0, 0, 0, 0);
                    grid[6] = average7(center, bL, bU, bTL, fL, fU, fTL);
                    grid[7] = average7(center, bU, fU, 0, 0, 0, 0);
                    grid[8] = average7(center, bR, bU, bTR, fR, fU, fTR);
                    for (int y = 0; y < 2; y++) {
                        for (int x = 0; x < 2; x++) {
                            int i = 3 * y + x;
                            float l = x * .5f, b = y * .5f, r = l + .5f, t = b + .5f;
                            int c0 = grid[i + 3], c1 = grid[i], c2 = grid[i + 1], c3 = grid[i + 4];
                            boolean flipDiag = chooseFlipDiag(c0, c1, c2, c3, x, y);
                            emitSubQuad(out, face, l, b, r, t,
                                    l, 1f - t, l, 1f - b, r, 1f - b, r, 1f - t,
                                    c0, c1, c2, c3,
                                    frameSprite, frameMat, flipDiag);
                        }
                    }
                    continue;
                }

                // Case 2: Pure Clear or Pure Stained/Tinted face
                boolean uniform = isSameOrZero(bL, center) && isSameOrZero(bR, center)
                        && isSameOrZero(bD, center) && isSameOrZero(bU, center)
                        && isSameOrZero(bBL, center) && isSameOrZero(bBR, center)
                        && isSameOrZero(bTL, center) && isSameOrZero(bTR, center)
                        && isSameOrZero(fL, center) && isSameOrZero(fR, center)
                        && isSameOrZero(fD, center) && isSameOrZero(fU, center)
                        && isSameOrZero(fBL, center) && isSameOrZero(fBR, center)
                        && isSameOrZero(fTL, center) && isSameOrZero(fTR, center);

                RenderMaterial material = clear ? clearMat : translucent;
                Sprite sprite = GlassSurface.sprite(atlas, mask, diagonals, false, clear);
                int emitCenter = clear ? CLEAR_COLOR : center;

                if (uniform) {
                    // Uniform-color face fast path: 1 quad instead of 4 (-75% vertices & translucent sort).
                    out.square(face, 0f, 0f, 1f, 1f, 0f);
                    out.uv(0, 0f, 0f).uv(1, 0f, 1f).uv(2, 1f, 1f).uv(3, 1f, 0f);
                    out.spriteBake(sprite, QuadEmitter.BAKE_NORMALIZED);
                    out.color(emitCenter, emitCenter, emitCenter, emitCenter);
                    out.material(material);
                    out.emit();
                    continue;
                }

                grid[0] = average7(center, bL, bD, bBL, fL, fD, fBL);
                grid[1] = average7(center, bD, fD, 0, 0, 0, 0);
                grid[2] = average7(center, bR, bD, bBR, fR, fD, fBR);
                grid[3] = average7(center, bL, fL, 0, 0, 0, 0);
                grid[4] = smoothCenter(center, bL, bR, bD, bU);
                grid[5] = average7(center, bR, fR, 0, 0, 0, 0);
                grid[6] = average7(center, bL, bU, bTL, fL, fU, fTL);
                grid[7] = average7(center, bU, fU, 0, 0, 0, 0);
                grid[8] = average7(center, bR, bU, bTR, fR, fU, fTR);

                for (int y = 0; y < 2; y++) {
                    for (int x = 0; x < 2; x++) {
                        int i = 3 * y + x;
                        float l = x * .5f, b = y * .5f, r = l + .5f, t = b + .5f;
                        int c0 = grid[i + 3], c1 = grid[i], c2 = grid[i + 1], c3 = grid[i + 4];
                        boolean flipDiag = chooseFlipDiag(c0, c1, c2, c3, x, y);
                        emitSubQuad(out, face, l, b, r, t,
                                l, 1f - t, l, 1f - b, r, 1f - b, r, 1f - t,
                                c0, c1, c2, c3,
                                sprite, material, flipDiag);
                    }
                }
            }
        }

        // Emits a 0.5x0.5 sub-quad. When flipDiag is true, shifts vertex indices by +1 (preserving CCW winding)
        // so the GPU triangle diagonal cuts across the outer corner instead of spiking into the block center!
        private static void emitSubQuad(QuadEmitter out, Direction face, float l, float b, float r, float t,
                                        float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3,
                                        int c0, int c1, int c2, int c3,
                                        Sprite sprite, RenderMaterial material, boolean flipDiag) {
            out.square(face, l, b, r, t, 0f);
            if (!flipDiag) {
                out.uv(0, u0, v0).uv(1, u1, v1).uv(2, u2, v2).uv(3, u3, v3);
                out.spriteBake(sprite, QuadEmitter.BAKE_NORMALIZED);
                out.color(c0, c1, c2, c3);
            } else {
                float x0 = out.x(0), y0 = out.y(0), z0 = out.z(0);
                float x1 = out.x(1), y1 = out.y(1), z1 = out.z(1);
                float x2 = out.x(2), y2 = out.y(2), z2 = out.z(2);
                float x3 = out.x(3), y3 = out.y(3), z3 = out.z(3);
                out.pos(0, x1, y1, z1).pos(1, x2, y2, z2).pos(2, x3, y3, z3).pos(3, x0, y0, z0);
                out.uv(0, u1, v1).uv(1, u2, v2).uv(2, u3, v3).uv(3, u0, v0);
                out.spriteBake(sprite, QuadEmitter.BAKE_NORMALIZED);
                out.color(c1, c2, c3, c0);
            }
            out.material(material);
            out.emit();
        }

        private static int findFirstTint(int... samples) {
            for (int s : samples) {
                if (isTintedOrStained(s)) return s;
            }
            return CLEAR_COLOR;
        }

        private static int sample2(BlockRenderView view, BlockPos.Mutable cursor, BlockPos pos,
                                   Direction a, Direction b) {
            cursor.set(
                    pos.getX() + a.getOffsetX() + b.getOffsetX(),
                    pos.getY() + a.getOffsetY() + b.getOffsetY(),
                    pos.getZ() + a.getOffsetZ() + b.getOffsetZ()
            );
            return glassSample(view.getBlockState(cursor));
        }

        private static int sample3(BlockRenderView view, BlockPos.Mutable cursor, BlockPos pos,
                                   Direction a, Direction b, Direction c) {
            cursor.set(
                    pos.getX() + a.getOffsetX() + b.getOffsetX() + c.getOffsetX(),
                    pos.getY() + a.getOffsetY() + b.getOffsetY() + c.getOffsetY(),
                    pos.getZ() + a.getOffsetZ() + b.getOffsetZ() + c.getOffsetZ()
            );
            return glassSample(view.getBlockState(cursor));
        }
    }
}
