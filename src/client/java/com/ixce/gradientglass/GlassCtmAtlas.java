package com.ixce.gradientglass;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.util.ArrayList;
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
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.slf4j.LoggerFactory;

final class GlassCtmAtlas implements SpriteSource {
    static final MapCodec<GlassCtmAtlas> CODEC = MapCodec.unit(new GlassCtmAtlas());
    // Standard CTM tile order. Bits circle the face from left to bottom to right to top.
    private static final int[] CONNECTIONS = {0,16,17,1,20,5,84,21,117,93,215,245,4,28,31,7,
            80,65,81,69,213,87,95,125,68,124,255,199,92,23,116,29,247,253,119,221,
            64,112,241,193,113,197,209,71,223,127,85};
    private static final int[] TILE = new int[256];
    static final Identifier[][] IDS = new Identifier[4][47];
    static {
        for (int tile = 0; tile < CONNECTIONS.length; tile++) {
            TILE[CONNECTIONS[tile]] = tile;
            for (int material = 0; material < IDS.length; material++)
                IDS[material][tile] = Identifier.fromNamespaceAndPath("gradientglass", "block/ctm_" + material + "_" + tile);
        }
    }

    static int tile(int edges, int diagonals) {
        int sides = ((edges & 1) == 0 ? 1 : 0) | ((edges & 4) == 0 ? 4 : 0)
                | ((edges & 2) == 0 ? 16 : 0) | ((edges & 8) == 0 ? 64 : 0);
        int corners = ((sides << 1) & ((sides | (sides << 8)) >> 1)) & diagonals & 170;
        return TILE[sides | corners];
    }

    @Override
    public MapCodec<GlassCtmAtlas> codec() { return CODEC; }

    @Override
    public void run(ResourceManager manager, Output atlas) {
        for (int mask = 0; mask < GradientGlass.CLEAR_TEXTURES.length; mask++) {
            final int edges = mask;
            var id = GradientGlass.CLEAR_TEXTURES[mask];
            atlas.add(id, opener -> clearGlass(id, edges));
        }
        Map<String, Integer> priority = new HashMap<>();
        manager.listPacks().forEachOrdered(p -> priority.put(p.packId(), priority.size()));
        var rules = new ArrayList<>(manager.listResources("optifine/ctm", id -> id.getPath().endsWith(".properties")).entrySet());
        rules.sort(Comparator.<Map.Entry<Identifier, Resource>>comparingInt(e ->
                priority.getOrDefault(e.getValue().sourcePackId(), -1)).reversed().thenComparing(Map.Entry::getKey));
        boolean[] found = new boolean[4];
        String[] blocks = {"glass", "white_stained_glass", "glass_pane", "white_stained_glass_pane"};
        for (var rule : rules) {
            if (rule.getValue().sourcePackId().equals("continuity:default")) continue;
            try (var stream = rule.getValue().open()) {
                Properties props = new Properties();
                props.load(stream);
                String method = props.getProperty("method", "ctm").trim();
                boolean compact = method.equals("ctm_compact");
                if (!compact && !method.equals("ctm")) continue;
                if (!props.stringPropertyNames().stream().allMatch(SUPPORTED::contains)) continue;
                for (int material = 0; material < blocks.length; material++) {
                    if (found[material] || !matches(props, rule.getKey(), blocks[material], material >= 2)) continue;
                    List<Resource> tiles = tiles(manager, props, rule.getKey(), compact ? 5 : 47);
                    if (tiles == null) continue;
                    if (compact) {
                        var sheet = Sheet.read(tiles);
                        if (sheet == null) continue;
                        for (int i = 0; i < 47; i++) {
                            int connections = CONNECTIONS[i];
                            var id = IDS[material][i];
                            atlas.add(id, opener -> sheet.bake(id, connections));
                        }
                    } else {
                        for (int i = 0; i < 47; i++) atlas.add(IDS[material][i], tiles.get(i));
                    }
                    found[material] = true;
                }
            } catch (IOException | IllegalArgumentException e) {
                LoggerFactory.getLogger("gradientglass").warn("Skipping glass CTM rule {}", rule.getKey(), e);
            }
            if (found[0] && found[1] && found[2] && found[3]) break;
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
        return new SpriteContents(id, new FrameSize(16, 16), image, ResourceMetadata.EMPTY);
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
        static Sheet read(List<Resource> tiles) throws IOException {
            int[][] pixels = new int[5][];
            int size = 0;
            for (int i = 0; i < 5; i++) {
                try (var stream = tiles.get(i).open(); var image = NativeImage.read(stream)) {
                    if (image.getHeight() != image.getWidth() || (i > 0 && image.getWidth() != size)) return null;
                    size = image.getWidth();
                    pixels[i] = image.getPixels();
                }
            }
            return new Sheet(size, pixels);
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
            return new SpriteContents(id, new FrameSize(size, size), image, ResourceMetadata.EMPTY);
        }
    }
}
