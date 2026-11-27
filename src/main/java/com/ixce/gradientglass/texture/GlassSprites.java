package com.ixce.gradientglass.texture;

import com.ixce.gradientglass.GlassMaterial;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
//? if <1.21.4 {
/*import com.ixce.gradientglass.mixin.SpriteSourcesAccessor;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
*///?}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GlassSprites implements SpriteSource {
    public static final MapCodec<GlassSprites> CODEC = MapCodec.unit(new GlassSprites());
    //? if <1.21.4 {
    /*public static final SpriteSourceType TYPE = SpriteSourcesAccessor.gradientglass$register("gradientglass:glass", CODEC);
    *///?}
    private static final Logger LOG = LoggerFactory.getLogger("gradientglass");
    private static final int MATERIALS = GlassMaterial.NAMES.length;

    // Slot m holds material m's block, slot MATERIALS + m its pane.
    static final ResourceLocation[][] CTM = new ResourceLocation[MATERIALS + GlassMaterial.PANES][Ctm.TILES];
    static final ResourceLocation[][] PLAIN = new ResourceLocation[MATERIALS][16];
    static final ResourceLocation[] PANE_TOPS = new ResourceLocation[GlassMaterial.PANES];
    // Only textures of the vanilla size have their borders covered where glass joins.
    static final int DEFAULT_SIZE = 16;

    record Loaded(Map<ResourceLocation, TextureNormalizer.Tint> tints, int[] averages, boolean[] replaced) {}

    static volatile Loaded loaded = new Loaded(Map.of(), new int[MATERIALS], new boolean[MATERIALS]);

    static {
        for (int slot = 0; slot < CTM.length; slot++) {
            for (int tile = 0; tile < Ctm.TILES; tile++) CTM[slot][tile] = id("ctm_" + slot + "_" + tile);
        }
        for (int material = 0; material < MATERIALS; material++) {
            for (int edges = 0; edges < 16; edges++) PLAIN[material][edges] = id("plain_" + material + "_" + edges);
        }
        for (int material = 1; material < GlassMaterial.PANES; material++) PANE_TOPS[material] = id("top_" + material);
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("gradientglass", "block/" + name);
    }

    private static String slotName(int slot) {
        return slot < MATERIALS ? GlassMaterial.NAMES[slot] : GlassMaterial.NAMES[slot - MATERIALS] + "_pane";
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.withDefaultNamespace("textures/block/" + name + ".png");
    }

    //? if >=1.21.4 {
    @Override
    public MapCodec<GlassSprites> codec() {
        return CODEC;
    }
    //?} else {
    /*@Override
    public SpriteSourceType type() {
        return TYPE;
    }
    *///?}

    @Override
    public void run(ResourceManager manager, Output atlas) {
        Map<String, Integer> packPriority = new HashMap<>();
        manager.listPacks().forEachOrdered(pack -> packPriority.put(pack.packId(), packPriority.size()));
        Map<ResourceLocation, TextureNormalizer.Tint> tints = new HashMap<>();
        int[] averages = new int[MATERIALS];
        boolean[] replaced = new boolean[MATERIALS];
        int[] plainPriority = new int[MATERIALS];
        Arrays.fill(plainPriority, -1);

        for (int material = 0; material < MATERIALS; material++) {
            var resource = manager.getResource(texture(GlassMaterial.NAMES[material])).orElse(null);
            if (resource == null) continue;
            try {
                var image = Pixels.read(resource);
                if (image == null) {
                    LOG.warn("Unsupported size for glass texture {}", GlassMaterial.NAMES[material]);
                    continue;
                }
                if (material != GlassMaterial.CLEAR) {
                    var normalizer = new TextureNormalizer(List.of(image));
                    averages[material] = normalizer.average();
                    image = normalizer.apply(image);
                    var tint = normalizer.tint(material, image);
                    for (var id : PLAIN[material]) tints.put(id, tint);
                }
                replaced[material] = !resource.sourcePackId().equals("vanilla");
                if (replaced[material]) plainPriority[material] = packPriority.getOrDefault(resource.sourcePackId(), -1);
                var pixels = image;
                // Larger textures may draw their borders at any width, so they keep them.
                for (int edges = pixels.size() > DEFAULT_SIZE ? 15 : 0; edges < 16; edges++) {
                    var id = PLAIN[material][edges];
                    int kept = edges;
                    atlas.add(id, opener -> pixels.withBorders(id, kept));
                }
            } catch (IOException | RuntimeException e) {
                LOG.warn("Could not read glass texture {}", GlassMaterial.NAMES[material], e);
            }
        }

        for (int material = 1; material < GlassMaterial.PANES; material++) {
            var resource = manager.getResource(texture(GlassMaterial.NAMES[material] + "_pane_top")).orElse(null);
            if (resource == null) continue;
            try {
                var image = Pixels.read(resource);
                if (image == null) continue;
                var normalizer = new TextureNormalizer(List.of(image));
                var top = normalizer.apply(image);
                var id = PANE_TOPS[material];
                tints.put(id, normalizer.tint(material, top));
                atlas.add(id, opener -> top.sprite(id));
            } catch (IOException | RuntimeException e) {
                LOG.warn("Could not read pane top {}", GlassMaterial.NAMES[material], e);
            }
        }

        var rules = new ArrayList<>(manager.listResources("optifine/ctm", id -> id.getPath().endsWith(".properties")).entrySet());
        rules.sort(Comparator.<Map.Entry<ResourceLocation, Resource>>comparingInt(rule ->
                packPriority.getOrDefault(rule.getValue().sourcePackId(), -1)).reversed().thenComparing(Map.Entry::getKey));
        boolean[] found = new boolean[CTM.length];
        int remaining = CTM.length;
        for (var rule : rules) {
            if (remaining == 0) break;
            // Continuity's built-in pack only imitates vanilla glass.
            if (rule.getValue().sourcePackId().equals("continuity:default")) continue;
            int rulePriority = packPriority.getOrDefault(rule.getValue().sourcePackId(), -1);
            try (var stream = rule.getValue().open()) {
                Properties props = new Properties();
                props.load(stream);
                if (!Ctm.supported(props)) continue;
                boolean compact = Ctm.compact(props);
                List<Resource> tiles = null;
                List<Pixels> images = null, normalized = null;
                TextureNormalizer normalizer = null;
                for (int slot = 0; slot < CTM.length; slot++) {
                    boolean pane = slot >= MATERIALS;
                    int material = pane ? slot - MATERIALS : slot;
                    if (found[slot] || !Ctm.matches(props, rule.getKey(), slotName(slot), pane)) continue;
                    // As in vanilla, a plain texture from a higher pack wins over this rule.
                    if (rulePriority < plainPriority[material]) continue;
                    if (tiles == null) tiles = Ctm.tiles(manager, props, rule.getKey(), compact ? 5 : Ctm.TILES);
                    if (tiles == null) break;
                    if (material == GlassMaterial.CLEAR && !compact) {
                        for (int i = 0; i < Ctm.TILES; i++) atlas.add(CTM[slot][i], tiles.get(i));
                    } else {
                        if (images == null) {
                            images = read(tiles);
                            if (images == null) break;
                            normalizer = new TextureNormalizer(images);
                            normalized = images.stream().map(normalizer::apply).toList();
                        }
                        var source = material == GlassMaterial.CLEAR ? images : normalized;
                        if (material != GlassMaterial.CLEAR) {
                            var tint = normalizer.tint(material, normalized.get(0));
                            for (var id : CTM[slot]) tints.put(id, tint);
                        }
                        if (compact) {
                            var sheet = Ctm.Compact.of(source);
                            if (sheet == null) continue;
                            for (int i = 0; i < Ctm.TILES; i++) {
                                var id = CTM[slot][i];
                                int connections = Ctm.CONNECTIONS[i];
                                atlas.add(id, opener -> sheet.tile(id, connections));
                            }
                        } else {
                            for (int i = 0; i < Ctm.TILES; i++) {
                                var id = CTM[slot][i];
                                var tile = source.get(i);
                                atlas.add(id, opener -> tile.sprite(id));
                            }
                        }
                    }
                    found[slot] = true;
                    remaining--;
                }
            } catch (IOException | IllegalArgumentException e) {
                LOG.warn("Skipping glass CTM rule {}", rule.getKey(), e);
            }
        }
        loaded = new Loaded(Map.copyOf(tints), averages, replaced);
    }

    private static List<Pixels> read(List<Resource> tiles) throws IOException {
        var images = new ArrayList<Pixels>(tiles.size());
        for (var tile : tiles) {
            var image = Pixels.read(tile);
            if (image == null) return null;
            images.add(image);
        }
        return images;
    }
}
