package com.ixce.gradientglass.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ixce.gradientglass.GradientGlass;
import com.ixce.gradientglass.model.PatternLayers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GlassConfig {
    private static final Logger LOG = LoggerFactory.getLogger("gradientglass");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("gradientglass.json");
    static final int MAX_SUBDIVISIONS = 8;

    static volatile GlassConfig current = new GlassConfig();

    float transitionWidth = 1.5f;
    int blockSubdivisions = 4;
    int paneRows = 4;
    int paneSegments = 2;
    boolean blur = true;
    int patternLayers = 2;
    boolean beaconGradient = true;
    boolean beaconDye = true;
    float weakChannel = 0.2f;
    int visibleAlpha = 32;

    public static boolean beaconGradient() {
        return current.beaconGradient;
    }

    public static boolean beaconDye() {
        return current.beaconDye;
    }

    GlassConfig copy() {
        return GSON.fromJson(GSON.toJson(this), GlassConfig.class);
    }

    boolean sameTextures(GlassConfig other) {
        return weakChannel == other.weakChannel && visibleAlpha == other.visibleAlpha;
    }

    private void clamp() {
        transitionWidth = Math.clamp(transitionWidth, 0.25f, GradientGlass.REACH);
        blockSubdivisions = Math.clamp(blockSubdivisions, 1, MAX_SUBDIVISIONS);
        paneRows = Math.clamp(paneRows, 1, MAX_SUBDIVISIONS);
        paneSegments = Math.clamp(paneSegments, 1, MAX_SUBDIVISIONS);
        patternLayers = Math.clamp(patternLayers, 0, PatternLayers.MAX_OTHERS);
        weakChannel = Math.clamp(weakChannel, 0, 1);
        visibleAlpha = Math.clamp(visibleAlpha, 0, 255);
    }

    static void load() {
        if (!Files.exists(PATH)) return;
        try {
            var config = GSON.fromJson(Files.readString(PATH), GlassConfig.class);
            config.clamp();
            current = config;
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not read {}", PATH, e);
        }
    }

    static void save(GlassConfig config) {
        config.clamp();
        current = config;
        try {
            Files.writeString(PATH, GSON.toJson(config));
        } catch (IOException e) {
            LOG.warn("Could not write {}", PATH, e);
        }
    }
}
