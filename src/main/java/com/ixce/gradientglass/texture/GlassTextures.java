package com.ixce.gradientglass.texture;

import com.ixce.gradientglass.GlassColor;
import com.ixce.gradientglass.GlassMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//? if >=1.21.11 {
/*import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}

public final class GlassTextures {
    private static final int MATERIALS = GlassMaterial.NAMES.length;
    private final GlassTexture[][] blocks = new GlassTexture[MATERIALS][Ctm.TILES];
    private final GlassTexture[][] panes = new GlassTexture[GlassMaterial.PANES][16];
    private final GlassTexture[] paneTops = new GlassTexture[GlassMaterial.PANES];
    private final float[][] colors = new float[MATERIALS][];
    private final boolean[][] samePattern = new boolean[MATERIALS][MATERIALS];

    public static final class Lazy {
        private volatile GlassTextures textures;

        public GlassTextures get() {
            var current = textures;
            if (current != null) return current;
            synchronized (this) {
                if (textures == null) {
                    //? if >=1.21.11 {
                    /*textures = new GlassTextures(Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS));
                    *///?} else {
                    textures = new GlassTextures(Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS));
                    //?}
                }
                return textures;
            }
        }
    }

    private GlassTextures(TextureAtlas atlas) {
        var loaded = GlassSprites.loaded;
        for (int material = 0; material < MATERIALS; material++) {
            for (int edges = 0; edges < 16; edges++) {
                for (int diagonals = 0; diagonals <= 170; diagonals += 2) {
                    if ((diagonals & ~170) != 0) continue;
                    int tile = Ctm.tile(edges, diagonals);
                    if (blocks[material][tile] == null) blocks[material][tile] = resolve(atlas, loaded, material, edges, tile, false);
                }
                if (material < GlassMaterial.PANES) {
                    panes[material][edges] = resolve(atlas, loaded, material, edges, Ctm.tile(edges, 255), true);
                }
            }
            if (material < GlassMaterial.PANES) {
                var top = material == GlassMaterial.CLEAR ? null : find(atlas, GlassSprites.PANE_TOPS[material]);
                if (top == null) top = vanilla(atlas, GlassMaterial.NAMES[material] + "_pane_top");
                paneTops[material] = texture(loaded, top, material);
            }
            if (material == GlassMaterial.CLEAR) continue;
            var tint = loaded.tints().get(blocks[material][0].sprite().contents().name());
            int average = tint != null && tint.material() == material ? tint.average() : loaded.averages()[material];
            colors[material] = GlassColor.lab(average != 0 ? average : 0xffffffff);
        }
        for (int a = 1; a < MATERIALS; a++) {
            var first = loaded.tints().get(blocks[a][0].sprite().contents().name());
            for (int b = 1; b < MATERIALS; b++) {
                var second = loaded.tints().get(blocks[b][0].sprite().contents().name());
                samePattern[a][b] = first == second || first == null || second == null || similar(first.sample(), second.sample());
            }
        }
    }

    // Normalized textures that differ this little look the same once tinted.
    private static final int PATTERN_TOLERANCE = 16;

    private static boolean similar(Pixels a, Pixels b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.argb().length; i++) {
            int x = a.argb()[i], y = b.argb()[i];
            if ((x >>> 24) < 32 && (y >>> 24) < 32) continue;
            for (int shift = 0; shift < 32; shift += 8) {
                if (Math.abs(((x >>> shift) & 255) - ((y >>> shift) & 255)) > PATTERN_TOLERANCE) return false;
            }
        }
        return true;
    }

    /** Whether two stained materials show the same pattern, so blending their textures would change nothing. */
    public boolean samePattern(int a, int b) {
        return samePattern[a][b];
    }

    public GlassTexture block(int material, int tile) {
        return blocks[material][tile];
    }

    public GlassTexture pane(int material, int edges) {
        return material < panes.length ? panes[material][edges] : blocks[material][Ctm.tile(edges, 255)];
    }

    public GlassTexture paneTop(int material) {
        return material < paneTops.length ? paneTops[material] : blocks[material][0];
    }

    public float[] color(int material) {
        return colors[material];
    }

    // White glass's CTM or pack texture outranks a color's vanilla texture, so a pack can cover all colors at once.
    private static GlassTexture resolve(TextureAtlas atlas, GlassSprites.Loaded loaded, int material, int edges, int tile, boolean pane) {
        boolean[] replaced = loaded.replaced();
        TextureAtlasSprite sprite = ctm(atlas, material, tile, pane);
        if (sprite == null && replaced[material]) sprite = plain(atlas, material, edges);
        if (sprite == null && material != GlassMaterial.CLEAR) {
            sprite = ctm(atlas, GlassMaterial.WHITE, tile, pane);
            if (sprite == null && replaced[GlassMaterial.WHITE]) sprite = plain(atlas, GlassMaterial.WHITE, edges);
        }
        if (sprite == null) sprite = plain(atlas, material, edges);
        if (sprite == null) sprite = vanilla(atlas, GlassMaterial.NAMES[material]);
        return texture(loaded, sprite, material);
    }

    private static GlassTexture texture(GlassSprites.Loaded loaded, TextureAtlasSprite sprite, int material) {
        boolean clear = material == GlassMaterial.CLEAR;
        var tint = clear ? null : loaded.tints().get(sprite.contents().name());
        return new GlassTexture(sprite, clear, tint == null ? null : tint.gain(), tint == null ? 1 : tint.alpha());
    }

    private static TextureAtlasSprite ctm(TextureAtlas atlas, int material, int tile, boolean pane) {
        TextureAtlasSprite sprite = pane ? find(atlas, GlassSprites.CTM[MATERIALS + material][tile]) : null;
        return sprite != null ? sprite : find(atlas, GlassSprites.CTM[material][tile]);
    }

    // A texture larger than the default keeps all its borders, so it only has the version with every border.
    private static TextureAtlasSprite plain(TextureAtlas atlas, int material, int edges) {
        var sprite = find(atlas, GlassSprites.PLAIN[material][edges]);
        return sprite != null ? sprite : find(atlas, GlassSprites.PLAIN[material][15]);
    }

    //? if >=1.21.11 {
    /*private static TextureAtlasSprite vanilla(TextureAtlas atlas, String name) {
        return atlas.getSprite(Identifier.withDefaultNamespace("block/" + name));
    }

    // The atlas returns its missing sprite for an absent id.
    private static TextureAtlasSprite find(TextureAtlas atlas, Identifier id) {
    *///?} else {
    private static TextureAtlasSprite vanilla(TextureAtlas atlas, String name) {
        return atlas.getSprite(ResourceLocation.withDefaultNamespace("block/" + name));
    }

    // The atlas returns its missing sprite for an absent id.
    private static TextureAtlasSprite find(TextureAtlas atlas, ResourceLocation id) {
    //?}
        var sprite = atlas.getSprite(id);
        return sprite.contents().name().equals(id) ? sprite : null;
    }
}
