package com.ixce.gradientglass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

record GlassSurface(TextureAtlasSprite clear, TextureAtlasSprite stained) {
    static final class Cache {
        private volatile Textures textures;

        Textures get() {
            var current = textures;
            return current != null ? current : initialize();
        }

        private synchronized Textures initialize() {
            if (textures == null) {
                textures = new Textures(Minecraft.getInstance()
                        .getAtlasManager().getAtlasOrThrow(TextureAtlas.LOCATION_BLOCKS));
            }
            return textures;
        }
    }

    static final class Textures {
        final GlassSurface[] blocks = new GlassSurface[47];
        final GlassSurface[] panes = new GlassSurface[16];
        final GlassSurface cap;

        Textures(TextureAtlas atlas) {
            for (int mask = 0; mask < 16; mask++) {
                for (int diagonals = 0; diagonals <= 170; diagonals++) {
                    if ((diagonals & ~170) != 0) continue;
                    int tile = GlassCtmAtlas.tile(mask, diagonals);
                    if (blocks[tile] == null) blocks[tile] = resolve(atlas, mask, tile, false);
                }
                panes[mask] = resolve(atlas, mask, GlassCtmAtlas.tile(mask, 255), true);
            }
            cap = new GlassSurface(atlas.getSprite(Identifier.withDefaultNamespace("block/glass_pane_top")),
                    atlas.getSprite(Identifier.withDefaultNamespace("block/white_stained_glass_pane_top")));
        }
    }

    private static GlassSurface resolve(TextureAtlas atlas, int mask, int tile, boolean pane) {
        TextureAtlasSprite clear = find(atlas, tile, 0, pane);
        TextureAtlasSprite stained = find(atlas, tile, 1, pane);
        if (clear == null && stained == null) {
            return new GlassSurface(pane ? null : atlas.getSprite(GradientGlass.CLEAR_TEXTURES[mask]),
                    atlas.getSprite(GradientGlass.TEXTURES[mask]));
        }
        if (clear == null) clear = atlas.getSprite(Identifier.withDefaultNamespace("block/glass"));
        if (stained == null) stained = atlas.getSprite(GradientGlass.TEXTURES[mask]);
        return new GlassSurface(clear, stained);
    }

    private static TextureAtlasSprite find(TextureAtlas atlas, int tile, int material, boolean pane) {
        if (pane) {
            Identifier id = GlassCtmAtlas.IDS[material + 2][tile];
            var sprite = atlas.getSprite(id);
            if (sprite.contents().name().equals(id)) return sprite;
        }
        Identifier id = GlassCtmAtlas.IDS[material][tile];
        var sprite = atlas.getSprite(id);
        return sprite.contents().name().equals(id) ? sprite : null;
    }

    TextureAtlasSprite sprite(int layer) {
        return layer == 0 ? clear : stained;
    }

    int tint(int argb, int layer) {
        if (clear == null) return argb;
        int weight = ((argb >>> 24) - 85) * 255 / 170;
        return layer == 0 ? ((255 - weight) << 24) | 0xffffff : (weight << 24) | (argb & 0xffffff);
    }
}
