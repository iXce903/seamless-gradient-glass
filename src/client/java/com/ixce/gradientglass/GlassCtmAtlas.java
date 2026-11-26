package com.ixce.gradientglass;

import com.mojang.serialization.Codec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteDimensions;
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.client.texture.atlas.AtlasSourceType;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.metadata.ResourceMetadata;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;

final class GlassCtmAtlas implements AtlasSource {
    static final Codec<GlassCtmAtlas> CODEC = Codec.unit(new GlassCtmAtlas());
    static final AtlasSourceType TYPE = new AtlasSourceType(CODEC);
    // Standard CTM tile order. Bits circle the face from left to bottom to right to top.
    private static final int[] CONNECTIONS = {0,16,17,1,20,5,84,21,117,93,215,245,4,28,31,7,
            80,65,81,69,213,87,95,125,68,124,255,199,92,23,116,29,247,253,119,221,
            64,112,241,193,113,197,209,71,223,127,85};
    private static final int[] TILE = new int[256];
    // Materials: 0=glass, 1=white_stained_glass, 2=glass_pane, 3=white_stained_glass_pane, 4=tinted_glass
    static final Identifier[][] IDS = new Identifier[5][47];
    // Stained CTM frame-only overlay (body alpha stripped to 0, preserving exact stained border & streak alpha).
    static final Identifier[] STAINED_FRAME_IDS = new Identifier[47];
    static final Identifier[] CLEAR_FALLBACK_IDS = new Identifier[16];
    static final Identifier[] CLEAR_FRAME_IDS = new Identifier[16];

    // 5-level quantized sub-quad alpha fade sprites deduplicated under D4 (4 rotations x 2 flips) => 119 unique 32x32 sprites!
    static final int FADE_LEVELS = 5;
    static final int FADE_RAW_COUNT = FADE_LEVELS * FADE_LEVELS * FADE_LEVELS * FADE_LEVELS;
    static final int[] FADE_CANON_ID = new int[FADE_RAW_COUNT];     // canonical sprite index (0 = empty, 1..uniqueCount)
    static final byte[] FADE_SYMMETRY = new byte[FADE_RAW_COUNT];   // 0..7 symmetry transform to map canonical UVs
    static final int FADE_UNIQUE_COUNT;
    static final Identifier[] FADE_UNIQUE_IDS;
    private static final int[] FADE_UNIQUE_CORNERS; // packed (qTL, qBL, qBR, qTR) for each unique id 1..N

    // UV corners (u, v) for each of the 8 D4 symmetries applied to quad vertices (v0=TL, v1=BL, v2=BR, v3=TR).
    // Canonical sprite has v0=(0,0), v1=(0,1), v2=(1,1), v3=(1,0).
    // If symmetry s maps canonical corners [c0, c1, c2, c3] -> target [t0, t1, t2, t3],
    // then quad vertex i samples canonical corner at position perm[s][i].
    private static final int[][] D4_PERM = {
        {0, 1, 2, 3}, // 0: identity
        {3, 0, 1, 2}, // 1: rot 90 CW
        {2, 3, 0, 1}, // 2: rot 180
        {1, 2, 3, 0}, // 3: rot 270 CW
        {3, 2, 1, 0}, // 4: flip H
        {0, 3, 2, 1}, // 5: flip H + rot 90
        {1, 0, 3, 2}, // 6: flip V
        {2, 1, 0, 3}  // 7: flip H + rot 270
    };
    private static final float[] CANON_U = {0f, 0f, 1f, 1f};
    private static final float[] CANON_V = {0f, 1f, 1f, 0f};

    static volatile int tintedRgb = GradientGlass.TINTED_COLOR;

    static {
        for (int tile = 0; tile < CONNECTIONS.length; tile++) {
            TILE[CONNECTIONS[tile]] = tile;
            for (int material = 0; material < IDS.length; material++) {
                IDS[material][tile] = new Identifier("gradientglass", "block/ctm_" + material + "_" + tile);
            }
            STAINED_FRAME_IDS[tile] = new Identifier("gradientglass", "block/ctm_sframe_" + tile);
        }
        for (int mask = 0; mask < 16; mask++) {
            CLEAR_FALLBACK_IDS[mask] = new Identifier("gradientglass", "block/clear_glass_" + mask);
            CLEAR_FRAME_IDS[mask] = new Identifier("gradientglass", "block/clear_frame_" + mask);
        }

        // Build D4 canonical lookup table (625 -> 119 unique non-zero patterns).
        Map<Integer, Integer> canonToUid = new HashMap<>();
        List<Integer> uniqueList = new ArrayList<>();
        uniqueList.add(0); // dummy at index 0
        int[] c = new int[4];
        for (int q0 = 0; q0 < FADE_LEVELS; q0++) {
            for (int q1 = 0; q1 < FADE_LEVELS; q1++) {
                for (int q2 = 0; q2 < FADE_LEVELS; q2++) {
                    for (int q3 = 0; q3 < FADE_LEVELS; q3++) {
                        int raw = fadeRawIndex(q0, q1, q2, q3);
                        if (raw == 0) continue;
                        c[0] = q0; c[1] = q1; c[2] = q2; c[3] = q3;
                        int bestCanon = Integer.MAX_VALUE;
                        int bestSym = 0;
                        for (int s = 0; s < 8; s++) {
                            // Find canonical corners C such that C[D4_PERM[s][i]] == c[i]
                            int[] inv = new int[4];
                            for (int i = 0; i < 4; i++) inv[D4_PERM[s][i]] = c[i];
                            int cand = fadeRawIndex(inv[0], inv[1], inv[2], inv[3]);
                            if (cand < bestCanon) {
                                bestCanon = cand;
                                bestSym = s;
                            }
                        }
                        Integer uid = canonToUid.get(bestCanon);
                        if (uid == null) {
                            uid = uniqueList.size();
                            canonToUid.put(bestCanon, uid);
                            uniqueList.add(bestCanon);
                        }
                        FADE_CANON_ID[raw] = uid;
                        FADE_SYMMETRY[raw] = (byte) bestSym;
                    }
                }
            }
        }
        FADE_UNIQUE_COUNT = uniqueList.size();
        FADE_UNIQUE_IDS = new Identifier[FADE_UNIQUE_COUNT];
        FADE_UNIQUE_CORNERS = new int[FADE_UNIQUE_COUNT];
        for (int i = 1; i < FADE_UNIQUE_COUNT; i++) {
            FADE_UNIQUE_IDS[i] = new Identifier("gradientglass", "block/fade32_" + i);
            FADE_UNIQUE_CORNERS[i] = uniqueList.get(i);
        }
    }

    static int fadeRawIndex(int qTL, int qBL, int qBR, int qTR) {
        return ((qTL * FADE_LEVELS + qBL) * FADE_LEVELS + qBR) * FADE_LEVELS + qTR;
    }

    static float fadeU(int sym, int vertex) {
        return CANON_U[D4_PERM[sym][vertex]];
    }

    static float fadeV(int sym, int vertex) {
        return CANON_V[D4_PERM[sym][vertex]];
    }

    static int tile(int edges, int diagonals) {
        int sides = ((edges & 1) == 0 ? 1 : 0) | ((edges & 4) == 0 ? 4 : 0)
                | ((edges & 2) == 0 ? 16 : 0) | ((edges & 8) == 0 ? 64 : 0);
        int corners = ((sides << 1) & ((sides | (sides << 8)) >> 1)) & diagonals & 170;
        return TILE[sides | corners];
    }

    @Override
    public AtlasSourceType getType() {
        return TYPE;
    }

    @Override
    public void load(ResourceManager manager, SpriteRegions atlas) {
        for (int mask = 0; mask < 16; mask++) {
            Identifier srcId = new Identifier("gradientglass", "textures/block/glass_" + mask + ".png");
            Identifier dstFallback = CLEAR_FALLBACK_IDS[mask];
            Identifier dstFrame = CLEAR_FRAME_IDS[mask];
            manager.getResource(srcId).ifPresent(res -> {
                atlas.add(dstFallback, opener -> bakeClearVariant(dstFallback, res, 38));
                atlas.add(dstFrame, opener -> bakeClearVariant(dstFrame, res, 0));
            });
        }

        Map<String, Integer> priority = new HashMap<>();
        manager.streamResourcePacks().forEachOrdered(p -> priority.put(p.getName(), priority.size()));
        var rules = new ArrayList<>(manager.findResources("optifine/ctm", id -> id.getPath().endsWith(".properties")).entrySet());
        rules.sort(Comparator.<Map.Entry<Identifier, Resource>>comparingInt(e ->
                priority.getOrDefault(e.getValue().getResourcePackName(), -1)).reversed().thenComparing(Map.Entry::getKey));
        boolean[] found = new boolean[5];
        String[] blocks = {"glass", "white_stained_glass", "glass_pane", "white_stained_glass_pane", "tinted_glass"};
        int stainedBodyAlpha = 112;
        int sampledTintedRgb = GradientGlass.TINTED_COLOR;

        for (var rule : rules) {
            if (rule.getValue().getResourcePackName().equals("continuity:default")) continue;
            try (var stream = rule.getValue().getInputStream()) {
                Properties props = new Properties();
                props.load(stream);
                String method = props.getProperty("method", "ctm").trim();
                boolean compact = method.equals("ctm_compact");
                if (!compact && !method.equals("ctm")) continue;
                if (!props.stringPropertyNames().stream().allMatch(SUPPORTED::contains)) continue;
                for (int material = 0; material < blocks.length; material++) {
                    boolean pane = material == 2 || material == 3;
                    if (found[material] || !matches(props, rule.getKey(), blocks[material], pane)) continue;
                    List<Resource> tiles = tiles(manager, props, rule.getKey(), compact ? 5 : 47);
                    if (tiles == null) continue;
                    if (compact) {
                        var sheet = Sheet.read(tiles);
                        if (sheet == null) continue;
                        if (material == 1) stainedBodyAlpha = sheet.centerAlpha();
                        if (material == 4) sampledTintedRgb = sheet.centerRgb();
                        int bodyA = stainedBodyAlpha;
                        for (int i = 0; i < 47; i++) {
                            int connections = CONNECTIONS[i];
                            var id = IDS[material][i];
                            atlas.add(id, opener -> sheet.bake(id, connections));
                            if (material == 1) {
                                var frameId = STAINED_FRAME_IDS[i];
                                atlas.add(frameId, opener -> sheet.bakeFrameOnly(frameId, connections, bodyA));
                            }
                        }
                    } else {
                        if (material == 1) {
                            int sampled = readCenterAlpha(tiles.get(26));
                            if (sampled > 0) stainedBodyAlpha = sampled;
                        }
                        if (material == 4) {
                            int rgb = readCenterRgb(tiles.get(26));
                            if (rgb != 0) sampledTintedRgb = rgb;
                        }
                        int bodyA = stainedBodyAlpha;
                        for (int i = 0; i < 47; i++) {
                            Resource tileRes = tiles.get(i);
                            atlas.add(IDS[material][i], tileRes);
                            if (material == 1) {
                                var frameId = STAINED_FRAME_IDS[i];
                                atlas.add(frameId, opener -> bakeStrippedFrame(frameId, tileRes, bodyA));
                            }
                        }
                    }
                    found[material] = true;
                }
            } catch (IOException | IllegalArgumentException e) {
                LoggerFactory.getLogger("gradientglass").warn("Skipping glass CTM rule {}", rule.getKey(), e);
            }
            if (found[0] && found[1] && found[2] && found[3] && found[4]) break;
        }

        tintedRgb = sampledTintedRgb;
        // Bake 119 D4-deduplicated 32x32 smooth bilinear alpha-fade sprites (64 steps across 1-block transition).
        final int bodyAlpha = stainedBodyAlpha;
        for (int uid = 1; uid < FADE_UNIQUE_COUNT; uid++) {
            int packed = FADE_UNIQUE_CORNERS[uid];
            int qTR = packed % FADE_LEVELS; packed /= FADE_LEVELS;
            int qBR = packed % FADE_LEVELS; packed /= FADE_LEVELS;
            int qBL = packed % FADE_LEVELS; packed /= FADE_LEVELS;
            int qTL = packed;
            Identifier id = FADE_UNIQUE_IDS[uid];
            atlas.add(id, opener -> bakeFadeSprite(id, qTL, qBL, qBR, qTR, bodyAlpha));
        }
    }

    private static int readCenterAlpha(Resource resource) {
        try (var stream = resource.getInputStream(); var img = NativeImage.read(stream)) {
            return (img.getColor(img.getWidth() / 2, img.getHeight() / 2) >>> 24) & 255;
        } catch (IOException e) {
            return 0;
        }
    }

    private static int readCenterRgb(Resource resource) {
        try (var stream = resource.getInputStream(); var img = NativeImage.read(stream)) {
            int abgr = img.getColor(img.getWidth() / 2, img.getHeight() / 2);
            int r = abgr & 255, g = (abgr >>> 8) & 255, b = (abgr >>> 16) & 255;
            return 0xff000000 | (r << 16) | (g << 8) | b;
        } catch (IOException e) {
            return 0;
        }
    }

    private static SpriteContents bakeStrippedFrame(Identifier id, Resource resource, int bodyAlpha) {
        try (var stream = resource.getInputStream(); var src = NativeImage.read(stream)) {
            int w = src.getWidth(), h = src.getHeight();
            var out = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int abgr = src.getColor(x, y);
                    int a = (abgr >>> 24) & 255;
                    int newA = a <= bodyAlpha + 4 ? 0 : Math.min(255, a - bodyAlpha);
                    out.setColor(x, y, (newA << 24) | (abgr & 0x00ffffff));
                }
            }
            return new SpriteContents(id, new SpriteDimensions(w, h), out, ResourceMetadata.NONE);
        } catch (IOException e) {
            LoggerFactory.getLogger("gradientglass").warn("Failed to bake stained frame {}", id, e);
            return null;
        }
    }

    private static SpriteContents bakeFadeSprite(Identifier id, int qTL, int qBL, int qBR, int qTR, int maxAlpha) {
        final int size = 32;
        var image = new NativeImage(size, size, false);
        double tl = qTL * 0.25, bl = qBL * 0.25, br = qBR * 0.25, tr = qTR * 0.25;
        for (int y = 0; y < size; y++) {
            double fy = (y + 0.5) / size;
            for (int x = 0; x < size; x++) {
                double fx = (x + 0.5) / size;
                double top = tl * (1.0 - fx) + tr * fx;
                double bot = bl * (1.0 - fx) + br * fx;
                double w = top * (1.0 - fy) + bot * fy;
                int a = Math.min(255, Math.max(0, (int) Math.round(w * maxAlpha)));
                image.setColor(x, y, (a << 24) | 0x00ffffff);
            }
        }
        return new SpriteContents(id, new SpriteDimensions(size, size), image, ResourceMetadata.NONE);
    }

    private static SpriteContents bakeClearVariant(Identifier id, Resource resource, int bodyAlpha) {
        try (var stream = resource.getInputStream(); var src = NativeImage.read(stream)) {
            int w = src.getWidth(), h = src.getHeight();
            var out = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int abgr = src.getColor(x, y);
                    int a = (abgr >>> 24) & 255;
                    int newA = a >= 200 ? 204 : a >= 140 ? 140 : bodyAlpha;
                    out.setColor(x, y, (newA << 24) | (abgr & 0x00ffffff));
                }
            }
            return new SpriteContents(id, new SpriteDimensions(w, h), out, ResourceMetadata.NONE);
        } catch (IOException e) {
            LoggerFactory.getLogger("gradientglass").warn("Failed to bake clear glass variant {}", id, e);
            return null;
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
        static Sheet read(List<Resource> tiles) throws IOException {
            int[][] pixels = new int[5][];
            int size = 0;
            for (int i = 0; i < 5; i++) {
                try (var stream = tiles.get(i).getInputStream(); var image = NativeImage.read(stream)) {
                    if (image.getHeight() != image.getWidth() || (i > 0 && image.getWidth() != size)) return null;
                    size = image.getWidth();
                    int[] px = new int[size * size];
                    for (int y = 0; y < size; y++) {
                        for (int x = 0; x < size; x++) {
                            px[y * size + x] = image.getColor(x, y);
                        }
                    }
                    pixels[i] = px;
                }
            }
            return new Sheet(size, pixels);
        }

        int centerAlpha() {
            int a = (pixels[1][(size / 2) * size + (size / 2)] >>> 24) & 255;
            return a > 0 ? a : 112;
        }

        int centerRgb() {
            int abgr = pixels[1][(size / 2) * size + (size / 2)];
            int r = abgr & 255, g = (abgr >>> 8) & 255, b = (abgr >>> 16) & 255;
            return 0xff000000 | (r << 16) | (g << 8) | b;
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
                    image.setColor(x, y, pixels[tile][y * size + x]);
                }
            }
            return new SpriteContents(id, new SpriteDimensions(size, size), image, ResourceMetadata.NONE);
        }

        SpriteContents bakeFrameOnly(Identifier id, int connections, int bodyAlpha) {
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
                    int abgr = pixels[tile][y * size + x];
                    int a = (abgr >>> 24) & 255;
                    int newA = a <= bodyAlpha + 4 ? 0 : Math.min(255, a - bodyAlpha);
                    image.setColor(x, y, (newA << 24) | (abgr & 0x00ffffff));
                }
            }
            return new SpriteContents(id, new SpriteDimensions(size, size), image, ResourceMetadata.NONE);
        }
    }
}
