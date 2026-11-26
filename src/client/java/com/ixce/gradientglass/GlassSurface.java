package com.ixce.gradientglass;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

// Cached sprite resolver for clear, stained, tinted, and 32x32 D4-deduplicated alpha-fade sprites.
// All emitted quads use vertex alpha 0xFF so Sodium 0.5.8 never darkens glass borders/sheen.
final class GlassSurface {
    private static final Identifier CLEAR_EDGE = new Identifier("minecraft", "block/glass_pane_top");
    private static final Identifier WHITE_EDGE = new Identifier("minecraft", "block/white_stained_glass_pane_top");
    private static volatile Cache cache;

    private GlassSurface() {}

    static void invalidate() {
        cache = null;
    }

    private static Cache cache(SpriteAtlasTexture atlas) {
        Cache c = cache;
        if (c != null) return c;
        c = buildCache(atlas);
        cache = c;
        return c;
    }

    private static Cache buildCache(SpriteAtlasTexture atlas) {
        Sprite[] defaultStained = new Sprite[16];
        Sprite[] defaultClear = new Sprite[16];
        Sprite[] defaultClearFrame = new Sprite[16];
        for (int mask = 0; mask < 16; mask++) {
            defaultStained[mask] = atlas.getSprite(GradientGlass.TEXTURES[mask]);
            Identifier clearId = GlassCtmAtlas.CLEAR_FALLBACK_IDS[mask];
            Sprite clearSprite = atlas.getSprite(clearId);
            defaultClear[mask] = clearSprite.getContents().getId().equals(clearId) ? clearSprite : defaultStained[mask];
            Identifier frameId = GlassCtmAtlas.CLEAR_FRAME_IDS[mask];
            Sprite frameSprite = atlas.getSprite(frameId);
            defaultClearFrame[mask] = frameSprite.getContents().getId().equals(frameId) ? frameSprite : defaultClear[mask];
        }

        Sprite[][] ctmClear = new Sprite[2][47];
        Sprite[][] ctmStained = new Sprite[2][47];
        Sprite[] ctmStainedFrame = new Sprite[47];
        boolean[] hasClearCtm = new boolean[2];
        boolean[] hasStainedCtm = new boolean[2];
        boolean hasStainedFrameCtm = false;

        for (int pane = 0; pane < 2; pane++) {
            boolean isPane = pane == 1;
            for (int tile = 0; tile < 47; tile++) {
                Sprite clear = find(atlas, tile, 0, isPane);
                Sprite stained = find(atlas, tile, 1, isPane);
                ctmClear[pane][tile] = clear;
                ctmStained[pane][tile] = stained;
                if (clear != null) hasClearCtm[pane] = true;
                if (stained != null) hasStainedCtm[pane] = true;
            }
        }
        for (int tile = 0; tile < 47; tile++) {
            Identifier id = GlassCtmAtlas.STAINED_FRAME_IDS[tile];
            Sprite s = atlas.getSprite(id);
            if (s.getContents().getId().equals(id)) {
                ctmStainedFrame[tile] = s;
                hasStainedFrameCtm = true;
            }
        }

        Sprite[] fadeSprites = new Sprite[GlassCtmAtlas.FADE_UNIQUE_COUNT];
        for (int i = 1; i < GlassCtmAtlas.FADE_UNIQUE_COUNT; i++) {
            Identifier id = GlassCtmAtlas.FADE_UNIQUE_IDS[i];
            Sprite s = atlas.getSprite(id);
            if (s.getContents().getId().equals(id)) {
                fadeSprites[i] = s;
            }
        }

        return new Cache(
                defaultClear,
                defaultClearFrame,
                defaultStained,
                ctmClear,
                ctmStained,
                ctmStainedFrame,
                fadeSprites,
                hasClearCtm,
                hasStainedCtm,
                hasStainedFrameCtm,
                atlas.getSprite(CLEAR_EDGE),
                atlas.getSprite(WHITE_EDGE)
        );
    }

    static Sprite sprite(SpriteAtlasTexture atlas, int mask, int diagonals, boolean pane, boolean clear) {
        Cache c = cache(atlas);
        int idx = pane ? 1 : 0;
        if (clear) {
            if (c.hasClearCtm[idx]) {
                Sprite s = c.ctmClear[idx][GlassCtmAtlas.tile(mask, diagonals)];
                if (s != null) return s;
            }
            return c.defaultClear[mask];
        }
        if (c.hasStainedCtm[idx]) {
            Sprite s = c.ctmStained[idx][GlassCtmAtlas.tile(mask, diagonals)];
            if (s != null) return s;
        }
        return c.defaultStained[mask];
    }

    // Frame + sheen overlay with 0% body alpha for Clear <-> Stained transition blocks.
    // On stained transition blocks, uses the stripped stained CTM frame so streaks match neighboring stained glass!
    static Sprite frameOverlay(SpriteAtlasTexture atlas, int mask, int diagonals, boolean pane, boolean clear) {
        Cache c = cache(atlas);
        if (!clear && !pane && c.hasStainedFrameCtm) {
            Sprite s = c.ctmStainedFrame[GlassCtmAtlas.tile(mask, diagonals)];
            if (s != null) return s;
        }
        int idx = pane ? 1 : 0;
        if (c.hasClearCtm[idx]) {
            Sprite s = c.ctmClear[idx][GlassCtmAtlas.tile(mask, diagonals)];
            if (s != null) return s;
        }
        return c.defaultClearFrame[mask];
    }

    static Sprite fadeSpriteByRaw(SpriteAtlasTexture atlas, int rawIndex) {
        int uid = GlassCtmAtlas.FADE_CANON_ID[rawIndex];
        return uid == 0 ? null : cache(atlas).fadeSprites[uid];
    }

    static boolean usesCutout(SpriteAtlasTexture atlas, boolean pane, boolean clear) {
        return clear && cache(atlas).hasClearCtm[pane ? 1 : 0];
    }

    static Sprite paneCap(SpriteAtlasTexture atlas, boolean pane, boolean clear) {
        Cache c = cache(atlas);
        if (clear) {
            return c.hasClearCtm[pane ? 1 : 0] ? c.clearEdge : c.defaultClear[0];
        }
        return c.hasStainedCtm[pane ? 1 : 0] ? c.whiteEdge : c.defaultStained[0];
    }

    private static Sprite find(SpriteAtlasTexture atlas, int tile, int material, boolean pane) {
        if (pane) {
            Identifier id = GlassCtmAtlas.IDS[material + 2][tile];
            var sprite = atlas.getSprite(id);
            if (sprite.getContents().getId().equals(id)) return sprite;
        }
        Identifier id = GlassCtmAtlas.IDS[material][tile];
        var sprite = atlas.getSprite(id);
        return sprite.getContents().getId().equals(id) ? sprite : null;
    }

    private record Cache(
            Sprite[] defaultClear,
            Sprite[] defaultClearFrame,
            Sprite[] defaultStained,
            Sprite[][] ctmClear,
            Sprite[][] ctmStained,
            Sprite[] ctmStainedFrame,
            Sprite[] fadeSprites,
            boolean[] hasClearCtm,
            boolean[] hasStainedCtm,
            boolean hasStainedFrameCtm,
            Sprite clearEdge,
            Sprite whiteEdge
    ) {}
}
