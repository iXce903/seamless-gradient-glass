package com.ixce.gradientglass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

final class GlassTextures {
    private static final int MATERIALS = GlassMaterial.NAMES.length;
    private final GlassTexture[][] blocks = new GlassTexture[MATERIALS][Ctm.TILES];
    private final GlassTexture[][] panes = new GlassTexture[GlassMaterial.PANES][16];
    private final GlassTexture[] paneTops = new GlassTexture[GlassMaterial.PANES];
    private final float[][] colors = new float[MATERIALS][];

    static final class Lazy {
        private volatile GlassTextures textures;

        GlassTextures get() {
            var current = textures;
            if (current != null) return current;
            synchronized (this) {
                if (textures == null) {
                    textures = new GlassTextures(Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS));
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
            var tint = loaded.tints().get(blocks[material][0].material().sprite().contents().name());
            int average = tint != null && tint.material() == material ? tint.average() : loaded.averages()[material];
            colors[material] = GlassColor.lab(average != 0 ? average : 0xffffffff);
        }
    }

    GlassTexture block(int material, int tile) {
        return blocks[material][tile];
    }

    GlassTexture pane(int material, int edges) {
        return panes[material][edges];
    }

    GlassTexture paneTop(int material) {
        return paneTops[material];
    }

    float[] color(int material) {
        return colors[material];
    }

    // White glass's CTM or pack texture outranks a color's vanilla texture, so a pack can cover all colors at once.
    private static GlassTexture resolve(TextureAtlas atlas, GlassSprites.Loaded loaded, int material, int edges, int tile, boolean pane) {
        boolean[] replaced = loaded.replaced();
        TextureAtlasSprite sprite = ctm(atlas, material, tile, pane);
        if (sprite == null && replaced[material]) sprite = find(atlas, GlassSprites.PLAIN[material][edges]);
        if (sprite == null && material != GlassMaterial.CLEAR) {
            sprite = ctm(atlas, GlassMaterial.WHITE, tile, pane);
            if (sprite == null && replaced[GlassMaterial.WHITE]) sprite = find(atlas, GlassSprites.PLAIN[GlassMaterial.WHITE][edges]);
        }
        if (sprite == null) sprite = find(atlas, GlassSprites.PLAIN[material][edges]);
        if (sprite == null) sprite = vanilla(atlas, GlassMaterial.NAMES[material]);
        return texture(loaded, sprite, material);
    }

    private static GlassTexture texture(GlassSprites.Loaded loaded, TextureAtlasSprite sprite, int material) {
        boolean clear = material == GlassMaterial.CLEAR;
        var tint = clear ? null : loaded.tints().get(sprite.contents().name());
        return new GlassTexture(new Material.Baked(sprite, true), clear, tint == null ? null : tint.gain());
    }

    private static TextureAtlasSprite ctm(TextureAtlas atlas, int material, int tile, boolean pane) {
        TextureAtlasSprite sprite = pane ? find(atlas, GlassSprites.CTM[MATERIALS + material][tile]) : null;
        return sprite != null ? sprite : find(atlas, GlassSprites.CTM[material][tile]);
    }

    private static TextureAtlasSprite vanilla(TextureAtlas atlas, String name) {
        return atlas.getSprite(Identifier.withDefaultNamespace("block/" + name));
    }

    // The atlas returns its missing sprite for an absent id.
    private static TextureAtlasSprite find(TextureAtlas atlas, Identifier id) {
        var sprite = atlas.getSprite(id);
        return sprite.contents().name().equals(id) ? sprite : null;
    }
}
