package com.ixce.gradientglass;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.DyeColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GlassCtmAtlas implements SpriteSource {
    static final MapCodec<GlassCtmAtlas> CODEC = MapCodec.unit(new GlassCtmAtlas());
    private static final Logger LOG = LoggerFactory.getLogger("gradientglass");
    // Standard CTM tile order. Bits circle the face from left to bottom to right to top.
    private static final int[] CONNECTIONS = {0,16,17,1,20,5,84,21,117,93,215,245,4,28,31,7,
            80,65,81,69,213,87,95,125,68,124,255,199,92,23,116,29,247,253,119,221,
            64,112,241,193,113,197,209,71,223,127,85};
    private static final int[] TILE = new int[256];

    /** Material 0 is clear glass, 1-16 the stained colors in DyeColor order, then tinted glass. */
    static final String[] MATERIALS = new String[DyeColor.values().length + 2];
    static final int WHITE = 1 + DyeColor.WHITE.ordinal(), TINTED = MATERIALS.length - 1;
    /** Materials below this index also have a pane (tinted glass has none). */
    static final int PANES = TINTED;
    /** CTM sprites: slot m holds material m's block, slot MATERIALS.length + m its pane. */
    static final Identifier[][] IDS = new Identifier[MATERIALS.length + PANES][47];
    /** A resource pack's plain texture per material, with the border of each joined side covered. */
    static final Identifier[][] PLAIN = new Identifier[MATERIALS.length][16];
    /** Stained pane tops, turned white so the gradient color can tint them. */
    static final Identifier[] CAPS = new Identifier[PANES];

    static {
        MATERIALS[0] = "glass";
        for (DyeColor dye : DyeColor.values()) MATERIALS[1 + dye.ordinal()] = dye.getSerializedName() + "_stained_glass";
        MATERIALS[TINTED] = "tinted_glass";
        for (int tile = 0; tile < CONNECTIONS.length; tile++) {
            TILE[CONNECTIONS[tile]] = tile;
            for (int slot = 0; slot < IDS.length; slot++)
                IDS[slot][tile] = Identifier.fromNamespaceAndPath("gradientglass", "block/ctm_" + slot + "_" + tile);
        }
        for (int material = 0; material < MATERIALS.length; material++) {
            for (int mask = 0; mask < 16; mask++)
                PLAIN[material][mask] = Identifier.fromNamespaceAndPath("gradientglass", "block/plain_" + material + "_" + mask);
        }
        for (int material = 1; material < PANES; material++)
            CAPS[material] = Identifier.fromNamespaceAndPath("gradientglass", "block/top_" + material);
    }

    static int tile(int edges, int diagonals) {
        int sides = ((edges & 1) == 0 ? 1 : 0) | ((edges & 4) == 0 ? 4 : 0)
                | ((edges & 2) == 0 ? 16 : 0) | ((edges & 8) == 0 ? 64 : 0);
        int corners = ((sides << 1) & ((sides | (sides << 8)) >> 1)) & diagonals & 170;
        return TILE[sides | corners];
    }

    private static String slotName(int slot) {
        return slot < MATERIALS.length ? MATERIALS[slot] : MATERIALS[slot - MATERIALS.length] + "_pane";
    }

    @Override
    public MapCodec<GlassCtmAtlas> codec() { return CODEC; }

    @Override
    public void run(ResourceManager manager, Output atlas) {
        Map<String, Integer> priority = new HashMap<>();
        manager.listPacks().forEachOrdered(p -> priority.put(p.packId(), priority.size()));

        // The mod's own clear glass, drawn in code.
        for (int mask = 0; mask < GradientGlass.CLEAR_TEXTURES.length; mask++) {
            final int edges = mask;
            var id = GradientGlass.CLEAR_TEXTURES[mask];
            atlas.add(id, opener -> clearGlass(id, edges));
        }

        // Plain textures that a resource pack replaces. Stained ones keep their pattern
        // but lose their own color, since the gradient supplies the color.
        int[] texturePriority = new int[MATERIALS.length];
        Arrays.fill(texturePriority, -1);
        for (int material = 0; material < MATERIALS.length; material++) {
            var resource = manager.getResource(Identifier.withDefaultNamespace("textures/block/" + MATERIALS[material] + ".png"))
                    .filter(r -> !r.sourcePackId().equals("vanilla")).orElse(null);
            if (resource == null) continue;
            texturePriority[material] = priority.getOrDefault(resource.sourcePackId(), -1);
            try {
                var image = Pixels.read(resource);
                if (image == null) {
                    LOG.warn("Unsupported size for resource pack texture {}", MATERIALS[material]);
                    continue;
                }
                final var texture = material == 0 ? image : image.whitened(image.brightest());
                for (int mask = 0; mask < 16; mask++) {
                    final int edges = mask;
                    var id = PLAIN[material][mask];
                    atlas.add(id, opener -> texture.connected(id, edges));
                }
            } catch (IOException | RuntimeException e) {
                LOG.warn("Could not read resource pack texture {}", MATERIALS[material], e);
            }
        }
        for (int material = 1; material < PANES; material++) {
            var resource = manager.getResource(Identifier.withDefaultNamespace("textures/block/" + MATERIALS[material] + "_pane_top.png"));
            if (resource.isEmpty()) continue;
            try {
                var image = Pixels.read(resource.get());
                if (image == null) continue;
                final var texture = image.whitened(image.brightest());
                var id = CAPS[material];
                atlas.add(id, opener -> texture.sprite(id));
            } catch (IOException | RuntimeException e) {
                LOG.warn("Could not read pane top {}", MATERIALS[material], e);
            }
        }

        var rules = new ArrayList<>(manager.listResources("optifine/ctm", id -> id.getPath().endsWith(".properties")).entrySet());
        rules.sort(Comparator.<Map.Entry<Identifier, Resource>>comparingInt(e ->
                priority.getOrDefault(e.getValue().sourcePackId(), -1)).reversed().thenComparing(Map.Entry::getKey));
        boolean[] found = new boolean[IDS.length];
        int remaining = IDS.length;
        for (var rule : rules) {
            // Continuity's default pack is the base game style; keep our original fallback for it.
            if (rule.getValue().sourcePackId().equals("continuity:default")) continue;
            int rulePriority = priority.getOrDefault(rule.getValue().sourcePackId(), -1);
            try (var stream = rule.getValue().open()) {
                Properties props = new Properties();
                props.load(stream);
                String method = props.getProperty("method", "ctm").trim();
                boolean compact = method.equals("ctm_compact");
                if (!compact && !method.equals("ctm")) continue;
                if (!props.stringPropertyNames().stream().allMatch(SUPPORTED::contains)) continue;
                List<Resource> tiles = null;
                for (int slot = 0; slot < IDS.length; slot++) {
                    boolean pane = slot >= MATERIALS.length;
                    int material = pane ? slot - MATERIALS.length : slot;
                    if (found[slot] || !matches(props, rule.getKey(), slotName(slot), pane)) continue;
                    // A higher-priority pack replaced the plain texture: it wins over this CTM, as in vanilla.
                    if (rulePriority < texturePriority[material]) continue;
                    if (tiles == null) tiles = tiles(manager, props, rule.getKey(), compact ? 5 : 47);
                    if (tiles == null) break;
                    if (compact) {
                        var sheet = Sheet.read(tiles, material != 0);
                        if (sheet == null) continue;
                        for (int i = 0; i < 47; i++) {
                            int connections = CONNECTIONS[i];
                            var id = IDS[slot][i];
                            atlas.add(id, opener -> sheet.bake(id, connections));
                        }
                    } else if (material != 0) {
                        // Every tile is scaled by the same reference so the 47 tiles stay consistent.
                        float reference = brightest(tiles);
                        for (int i = 0; i < 47; i++) {
                            var tile = tiles.get(i);
                            var id = IDS[slot][i];
                            atlas.add(id, opener -> whitened(id, tile, reference));
                        }
                    } else {
                        for (int i = 0; i < 47; i++) atlas.add(IDS[slot][i], tiles.get(i));
                    }
                    found[slot] = true;
                    remaining--;
                }
            } catch (IOException | IllegalArgumentException e) {
                LOG.warn("Skipping glass CTM rule {}", rule.getKey(), e);
            }
            if (remaining == 0) break;
        }
    }

    private static SpriteContents clearGlass(Identifier id, int edges) {
        var image = new NativeImage(16, 16, false);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean border = (x == 0 && (edges & 1) != 0) || (x == 15 && (edges & 2) != 0)
                        || (y == 15 && (edges & 4) != 0) || (y == 0 && (edges & 8) != 0);
                boolean reflection = (x + y == 10 && x >= 2 && x <= 6)
                        || (x + y == 13 && x >= 5 && x <= 7) || (x + y == 22 && x >= 10 && x <= 12);
                int argb = 0x10e3f5ff;
                if (reflection) argb = 0xb0deeff4;
                if (border) argb = x == 0 || y == 0 ? 0xe4c7e6ed : 0xdd83b6c4;
                image.setPixel(x, y, argb);
            }
        }
        return new SpriteContents(id, new FrameSize(16, 16), image);
    }

    private static float brightest(List<Resource> tiles) throws IOException {
        float reference = 0;
        for (var tile : tiles) {
            var image = Pixels.read(tile);
            if (image != null) reference = Math.max(reference, image.brightest());
        }
        return reference;
    }

    private static SpriteContents whitened(Identifier id, Resource tile, float reference) {
        try {
            var image = Pixels.read(tile);
            return image == null ? null : image.whitened(reference).sprite(id);
        } catch (IOException e) {
            LOG.warn("Could not read glass CTM tile {}", id, e);
            return null;
        }
    }

    /** One square frame of a texture, as ARGB pixels in rows from the top. */
    private record Pixels(int size, int[] argb) {
        /** Reads the first frame; returns null unless the height is a whole number of square frames. */
        static Pixels read(Resource resource) throws IOException {
            try (var stream = resource.open(); var image = NativeImage.read(stream)) {
                int size = image.getWidth(), height = image.getHeight();
                if (size == 0 || height % size != 0) return null;
                return new Pixels(size, Arrays.copyOf(image.getPixels(), size * size));
            }
        }

        private static float luminance(int argb) {
            return 0.2126f * ((argb >>> 16) & 255) + 0.7152f * ((argb >>> 8) & 255) + 0.0722f * (argb & 255);
        }

        /** Brightest visible pixel; nearly transparent pixels can carry arbitrary color and are ignored. */
        float brightest() {
            float max = 0;
            for (int pixel : argb) if ((pixel >>> 24) >= 32) max = Math.max(max, luminance(pixel));
            return max;
        }

        /**
         * Removes the texture's own color but keeps its light and dark pattern and its alpha:
         * the reference brightness becomes white, darker pixels become proportionally gray.
         * The gradient's vertex color then tints it like the mod's own white textures.
         */
        Pixels whitened(float reference) {
            int[] out = new int[argb.length];
            for (int i = 0; i < argb.length; i++) {
                int pixel = argb[i];
                int gray = reference <= 0 ? 255 : Math.min(255, Math.round(luminance(pixel) * 255 / reference));
                out[i] = (pixel & 0xff000000) | gray * 0x010101;
            }
            return new Pixels(size, out);
        }

        SpriteContents sprite(Identifier id) {
            return connected(id, 15);
        }

        /** Bits in edges keep the border (1 left, 2 right, 4 bottom, 8 top); cleared sides join more glass. */
        SpriteContents connected(Identifier id, int edges) {
            // Border width scales with resolution: 1px at 16x, 2px at 32x, and so on.
            int b = Math.max(1, size / 16);
            var image = new NativeImage(size, size, false);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int sx = x, sy = y;
                    // Cover the border of a joined side with the pixel just inside it.
                    if ((edges & 1) == 0 && x < b) sx = b;
                    if ((edges & 2) == 0 && x >= size - b) sx = size - b - 1;
                    if ((edges & 8) == 0 && y < b) sy = b;
                    if ((edges & 4) == 0 && y >= size - b) sy = size - b - 1;
                    image.setPixel(x, y, argb[sy * size + sx]);
                }
            }
            return new SpriteContents(id, new FrameSize(size, size), image);
        }
    }

    private static final Set<String> SUPPORTED = Set.of(
            "matchBlocks", "matchTiles", "method", "tiles", "faces", "connect");

    private static boolean matches(Properties props, Identifier rule, String block, boolean pane) {
        String faces = props.getProperty("faces", "all").trim();
        if (!faces.equals("all") && !(pane && faces.equals("sides"))) return false;
        if (!List.of("block", "tile", "state").contains(props.getProperty("connect", "block").trim())) return false;
        String blockNames = props.getProperty("matchBlocks"), textures = props.getProperty("matchTiles");
        String texture = pane ? block.substring(0, block.length() - 5) : block;
        if (blockNames == null && textures == null) {
            String path = rule.getPath();
            return path.substring(path.lastIndexOf('/') + 1).equals(texture + ".properties");
        }
        return (blockNames == null || matchesName(blockNames, block, false))
                && (textures == null || matchesName(textures, texture, true));
    }

    private static boolean matchesName(String list, String target, boolean texture) {
        for (String token : list.trim().split("\\s+")) {
            String name = token.replaceFirst("^minecraft:", "");
            if (texture) name = name.replaceFirst("^(textures/)?block/", "").replaceFirst("\\.png$", "");
            if (name.equals(target)) return true;
        }
        return false;
    }

    private static List<Resource> tiles(ResourceManager manager, Properties props, Identifier rule, int count) {
        var result = new ArrayList<Resource>(count);
        for (String token : props.getProperty("tiles", "").trim().split("\\s+")) {
            if (token.isEmpty()) break;
            if (token.matches("[0-9]+-[0-9]+")) {
                String[] ends = token.split("-");
                long start = Long.parseLong(ends[0]), end = Long.parseLong(ends[1]);
                for (long i = start; i <= end && result.size() < count; i++) {
                    if (!addTile(manager, rule, Long.toString(i), result)) return null;
                    if (i == end) break;
                }
            } else if (!addTile(manager, rule, token, result)) return null;
            if (result.size() == count) return result;
        }
        return null;
    }

    private static boolean addTile(ResourceManager manager, Identifier rule, String token, List<Resource> result) {
        String ns = rule.getNamespace();
        int colon = token.indexOf(':');
        if (colon >= 0) {
            ns = token.substring(0, colon);
            token = token.substring(colon + 1);
        }
        String path;
        if (token.startsWith("~/")) path = "optifine/" + token.substring(2);
        else if (token.startsWith("/")) path = token.substring(1);
        else if (token.startsWith("textures/") || token.startsWith("optifine/")) path = token;
        else if (colon >= 0 || token.startsWith("block/")) path = "textures/" + token;
        else path = rule.getPath().substring(0, rule.getPath().lastIndexOf('/') + 1) + token.replaceFirst("^\\./", "");
        var id = Identifier.tryParse(ns + ":" + path + (path.endsWith(".png") ? "" : ".png"));
        if (id == null) return false;
        var resource = manager.getResource(id);
        resource.ifPresent(result::add);
        return resource.isPresent();
    }

    private record Sheet(int size, int[][] pixels) {
        static Sheet read(List<Resource> tiles, boolean whiten) throws IOException {
            var images = new Pixels[5];
            float reference = 0;
            for (int i = 0; i < 5; i++) {
                images[i] = Pixels.read(tiles.get(i));
                if (images[i] == null || images[i].size() != images[0].size()) return null;
                reference = Math.max(reference, images[i].brightest());
            }
            int[][] pixels = new int[5][];
            for (int i = 0; i < 5; i++) pixels[i] = (whiten ? images[i].whitened(reference) : images[i]).argb();
            return new Sheet(images[0].size(), pixels);
        }

        SpriteContents bake(Identifier id, int connections) {
            var image = new NativeImage(size, size, false);
            for (int y = 0; y < size; y++) {
                boolean bottom = y >= size / 2;
                boolean vertical = (connections & (bottom ? 4 : 64)) != 0;
                for (int x = 0; x < size; x++) {
                    boolean left = x < size / 2;
                    boolean horizontal = (connections & (left ? 1 : 16)) != 0;
                    int diagonal = bottom ? (left ? 2 : 8) : (left ? 128 : 32);
                    int tile = horizontal && vertical ? ((connections & diagonal) == 0 ? 4 : 1)
                            : horizontal ? 3 : vertical ? 2 : 0;
                    image.setPixel(x, y, pixels[tile][y * size + x]);
                }
            }
            return new SpriteContents(id, new FrameSize(size, size), image);
        }
    }
}
