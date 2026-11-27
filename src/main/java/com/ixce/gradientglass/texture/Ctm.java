package com.ixce.gradientglass.texture;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

final class Ctm {
    // Standard tile order. Bits circle the face from left to bottom to right to top:
    // sides are 1, 4, 16, 64 and the corners between them 2, 8, 32, 128.
    static final int[] CONNECTIONS = {0,16,17,1,20,5,84,21,117,93,215,245,4,28,31,7,
            80,65,81,69,213,87,95,125,68,124,255,199,92,23,116,29,247,253,119,221,
            64,112,241,193,113,197,209,71,223,127,85};
    static final int TILES = CONNECTIONS.length;
    private static final int[] TILE = new int[256];
    private static final Set<String> SUPPORTED = Set.of("matchBlocks", "matchTiles", "method", "tiles", "faces", "connect");

    static {
        for (int tile = 0; tile < TILES; tile++) TILE[CONNECTIONS[tile]] = tile;
    }

    private Ctm() {}

    // edges: 1 left, 2 right, 4 bottom, 8 top, set where the face is not joined.
    static int tile(int edges, int diagonals) {
        int sides = ((edges & 1) == 0 ? 1 : 0) | ((edges & 4) == 0 ? 4 : 0)
                | ((edges & 2) == 0 ? 16 : 0) | ((edges & 8) == 0 ? 64 : 0);
        // A corner only counts when both sides next to it are joined.
        int corners = ((sides << 1) & ((sides | (sides << 8)) >> 1)) & diagonals & 170;
        return TILE[sides | corners];
    }

    static boolean supported(Properties props) {
        String method = props.getProperty("method", "ctm").trim();
        return (method.equals("ctm") || method.equals("ctm_compact"))
                && props.stringPropertyNames().stream().allMatch(SUPPORTED::contains);
    }

    static boolean compact(Properties props) {
        return props.getProperty("method", "ctm").trim().equals("ctm_compact");
    }

    static boolean matches(Properties props, Identifier rule, String block, boolean pane) {
        String faces = props.getProperty("faces", "all").trim();
        if (!faces.equals("all") && !(pane && faces.equals("sides"))) return false;
        if (!List.of("block", "tile", "state").contains(props.getProperty("connect", "block").trim())) return false;
        String blocks = props.getProperty("matchBlocks"), textures = props.getProperty("matchTiles");
        String texture = pane ? block.substring(0, block.length() - "_pane".length()) : block;
        if (blocks == null && textures == null) {
            String path = rule.getPath();
            return path.substring(path.lastIndexOf('/') + 1).equals(texture + ".properties");
        }
        return (blocks == null || listed(blocks, block, false)) && (textures == null || listed(textures, texture, true));
    }

    private static boolean listed(String list, String target, boolean texture) {
        for (String token : list.trim().split("\\s+")) {
            String name = token.replaceFirst("^minecraft:", "");
            if (texture) name = name.replaceFirst("^(textures/)?block/", "").replaceFirst("\\.png$", "");
            if (name.equals(target)) return true;
        }
        return false;
    }

    static List<Resource> tiles(ResourceManager manager, Properties props, Identifier rule, int count) {
        var result = new ArrayList<Resource>(count);
        for (String token : props.getProperty("tiles", "").trim().split("\\s+")) {
            if (token.isEmpty()) break;
            if (token.matches("[0-9]+-[0-9]+")) {
                String[] ends = token.split("-");
                long start = Long.parseLong(ends[0]), end = Long.parseLong(ends[1]);
                for (long i = start; i <= end && result.size() < count; i++) {
                    if (!addTile(manager, rule, Long.toString(i), result)) return null;
                }
            } else if (!addTile(manager, rule, token, result)) return null;
            if (result.size() == count) return result;
        }
        return null;
    }

    private static boolean addTile(ResourceManager manager, Identifier rule, String token, List<Resource> result) {
        String namespace = rule.getNamespace();
        int colon = token.indexOf(':');
        if (colon >= 0) {
            namespace = token.substring(0, colon);
            token = token.substring(colon + 1);
        }
        String path;
        if (token.startsWith("~/")) path = "optifine/" + token.substring(2);
        else if (token.startsWith("/")) path = token.substring(1);
        else if (token.startsWith("textures/") || token.startsWith("optifine/")) path = token;
        else if (colon >= 0 || token.startsWith("block/")) path = "textures/" + token;
        else path = rule.getPath().substring(0, rule.getPath().lastIndexOf('/') + 1) + token.replaceFirst("^\\./", "");
        var id = Identifier.tryParse(namespace + ":" + path + (path.endsWith(".png") ? "" : ".png"));
        if (id == null) return false;
        var resource = manager.getResource(id);
        resource.ifPresent(result::add);
        return resource.isPresent();
    }

    record Compact(int size, int[][] pixels) {
        static Compact of(List<Pixels> images) {
            int[][] pixels = new int[5][];
            for (int i = 0; i < 5; i++) {
                if (images.get(i).size() != images.get(0).size()) return null;
                pixels[i] = images.get(i).argb();
            }
            return new Compact(images.get(0).size(), pixels);
        }

        SpriteContents tile(Identifier id, int connections) {
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
