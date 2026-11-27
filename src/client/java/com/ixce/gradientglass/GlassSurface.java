package com.ixce.gradientglass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

// Each surface crossfades an ordinary glass layer with one stained glass layer.
// In the existing vertex grid ordinary glass has alpha 85, all dyes have alpha 255.
record GlassSurface(Material.Baked clear, Material.Baked stained) {
    // Gradient color of each material (index as in GlassCtmAtlas.MATERIALS); 0 for clear glass.
    private static final int[] COLORS = new int[GlassCtmAtlas.MATERIALS.length];

    static {
        for (DyeColor dye : DyeColor.values()) COLORS[1 + dye.ordinal()] = 0xff000000 | dye.getTextureDiffuseColor();
        COLORS[GlassCtmAtlas.TINTED] = GradientGlass.TINTED_COLOR;
    }

    static final class Cache {
        private volatile Textures textures;

        Textures get() {
            var current = textures;
            return current != null ? current : initialize();
        }

        private synchronized Textures initialize() {
            if (textures == null) {
                textures = new Textures(Minecraft.getInstance()
                        .getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS));
            }
            return textures;
        }
    }

    // Indexed by stained material first (1 to MATERIALS.length - 1; index 0 is unused).
    static final class Textures {
        final GlassSurface[][] blocks = new GlassSurface[GlassCtmAtlas.MATERIALS.length][47];
        final GlassSurface[][] panes = new GlassSurface[GlassCtmAtlas.PANES][16];
        final GlassSurface[] caps = new GlassSurface[GlassCtmAtlas.PANES];

        Textures(TextureAtlas atlas) {
            Material.Baked paneTop = baked(atlas.getSprite(Identifier.withDefaultNamespace("block/glass_pane_top")));
            TextureAtlasSprite whiteTop = atlas.getSprite(Identifier.withDefaultNamespace("block/white_stained_glass_pane_top"));
            for (int material = 1; material < blocks.length; material++) {
                for (int mask = 0; mask < 16; mask++) {
                    for (int diagonals = 0; diagonals <= 170; diagonals++) {
                        if ((diagonals & ~170) != 0) continue;
                        int tile = GlassCtmAtlas.tile(mask, diagonals);
                        if (blocks[material][tile] == null) blocks[material][tile] = resolve(atlas, material, mask, tile, false);
                    }
                    if (material < panes.length)
                        panes[material][mask] = resolve(atlas, material, mask, GlassCtmAtlas.tile(mask, 255), true);
                }
                if (material < caps.length) {
                    TextureAtlasSprite top = find(atlas, GlassCtmAtlas.CAPS[material]);
                    caps[material] = new GlassSurface(paneTop, baked(top != null ? top : whiteTop));
                }
            }
        }
    }

    /** The material whose stained texture a face uses: its own block's, or for clear glass its first stained neighbor's. */
    static int stainedMaterial(int center, int a, int b, int c, int d) {
        int material = materialOf(center);
        if (material == 0) material = materialOf(a);
        if (material == 0) material = materialOf(b);
        if (material == 0) material = materialOf(c);
        if (material == 0) material = materialOf(d);
        return material == 0 ? GlassCtmAtlas.WHITE : material;
    }

    private static int materialOf(int color) {
        if ((color >>> 24) != 255) return 0; // Clear glass, a beacon, or no glass at all.
        for (int material = 1; material < COLORS.length; material++) {
            if (COLORS[material] == color) return material;
        }
        return GlassCtmAtlas.WHITE;
    }

    private static Material.Baked baked(TextureAtlasSprite sprite) {
        return sprite == null ? null : new Material.Baked(sprite, true);
    }

    // Stained layer, most specific first: this color's CTM, this color's plain pack texture,
    // then white's CTM and white's plain texture (shared by all colors), then the mod's own.
    private static GlassSurface resolve(TextureAtlas atlas, int material, int mask, int tile, boolean pane) {
        TextureAtlasSprite stainedCtm = ctm(atlas, material, tile, pane);
        TextureAtlasSprite stained = stainedCtm != null ? stainedCtm : find(atlas, GlassCtmAtlas.PLAIN[material][mask]);
        if (stained == null && material != GlassCtmAtlas.WHITE) {
            stainedCtm = ctm(atlas, GlassCtmAtlas.WHITE, tile, pane);
            stained = stainedCtm != null ? stainedCtm : find(atlas, GlassCtmAtlas.PLAIN[GlassCtmAtlas.WHITE][mask]);
        }
        TextureAtlasSprite clear = ctm(atlas, 0, tile, pane);
        if (clear == null) clear = find(atlas, GlassCtmAtlas.PLAIN[0][mask]);
        if (clear == null) {
            if (stainedCtm != null) clear = atlas.getSprite(Identifier.withDefaultNamespace("block/glass"));
            else if (!pane) clear = atlas.getSprite(GradientGlass.CLEAR_TEXTURES[mask]);
        }
        if (stained == null) stained = atlas.getSprite(GradientGlass.TEXTURES[mask]);
        return new GlassSurface(baked(clear), baked(stained));
    }

    // A pane without its own rule reuses the full block's.
    private static TextureAtlasSprite ctm(TextureAtlas atlas, int material, int tile, boolean pane) {
        if (pane) {
            TextureAtlasSprite sprite = find(atlas, GlassCtmAtlas.IDS[GlassCtmAtlas.MATERIALS.length + material][tile]);
            if (sprite != null) return sprite;
        }
        return find(atlas, GlassCtmAtlas.IDS[material][tile]);
    }

    // The atlas returns its missing sprite for an absent id.
    private static TextureAtlasSprite find(TextureAtlas atlas, Identifier id) {
        var sprite = atlas.getSprite(id);
        return sprite.contents().name().equals(id) ? sprite : null;
    }

    Material.Baked material(int layer) {
        return layer == 0 ? clear : stained;
    }

    int tint(int argb, int layer) {
        if (clear == null) return argb;
        int weight = ((argb >>> 24) - 85) * 255 / 170;
        return layer == 0 ? ((255 - weight) << 24) | 0xffffff : (weight << 24) | (argb & 0xffffff);
    }
}
