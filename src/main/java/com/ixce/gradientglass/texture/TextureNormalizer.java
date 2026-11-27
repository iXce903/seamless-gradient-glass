package com.ixce.gradientglass.texture;

import com.ixce.gradientglass.config.GlassConfig;
import java.util.List;

final class TextureNormalizer {
    // sample is one normalized image, to compare patterns; alpha is the mean opacity of the images.
    record Tint(int material, int average, float[] gain, float alpha, Pixels sample) {}

    private final float[] max = new float[3];
    private final boolean[] borrow = new boolean[3];
    private float maxLuminance;
    private final int average;
    private final float[] gain = new float[3];
    private final float alpha;

    TextureNormalizer(List<Pixels> images) {
        // Pixels below this alpha can carry arbitrary color and do not set the maximum.
        int visibleAlpha = GlassConfig.current.visibleAlpha;
        // A channel darker than this fraction of the brightest one borrows the luminance pattern,
        // otherwise tinting toward that channel's hue would show its noise.
        float weakChannel = GlassConfig.current.weakChannel;
        for (var image : images) {
            for (int pixel : image.argb()) {
                if ((pixel >>> 24) < visibleAlpha) continue;
                for (int c = 0; c < 3; c++) max[c] = Math.max(max[c], channel(pixel, c));
                maxLuminance = Math.max(maxLuminance, luminance(pixel));
            }
        }
        float top = Math.max(max[0], Math.max(max[1], max[2]));
        for (int c = 0; c < 3; c++) borrow[c] = max[c] <= 0 || max[c] < weakChannel * top;
        double weight = 0;
        int pixels = 0;
        double[] sum = new double[3], normalized = new double[3];
        for (var image : images) {
            for (int pixel : image.argb()) {
                int alpha = pixel >>> 24;
                weight += alpha;
                pixels++;
                int out = normalize(pixel);
                for (int c = 0; c < 3; c++) {
                    sum[c] += alpha * channel(pixel, c);
                    normalized[c] += alpha * channel(out, c);
                }
            }
        }
        int rgb = 0;
        for (int c = 0; c < 3; c++) {
            rgb |= (weight > 0 ? (int) Math.round(sum[c] / weight) : 255) << (16 - 8 * c);
            gain[c] = normalized[c] > 0 ? (float) (255 * weight / normalized[c]) : 1;
        }
        average = 0xff000000 | rgb;
        alpha = pixels > 0 ? (float) (weight / (255.0 * pixels)) : 1;
    }

    int average() {
        return average;
    }

    Tint tint(int material, Pixels sample) {
        return new Tint(material, average, gain.clone(), alpha, sample);
    }

    Pixels apply(Pixels image) {
        int[] out = new int[image.argb().length];
        for (int i = 0; i < out.length; i++) out[i] = normalize(image.argb()[i]);
        return new Pixels(image.size(), out);
    }

    private int normalize(int pixel) {
        float gray = maxLuminance > 0 ? luminance(pixel) / maxLuminance : 1;
        int out = pixel & 0xff000000;
        for (int c = 0; c < 3; c++) {
            float value = borrow[c] ? gray : channel(pixel, c) / max[c];
            out |= Math.min(255, Math.round(value * 255)) << (16 - 8 * c);
        }
        return out;
    }

    private static float luminance(int argb) {
        return 0.2126f * ((argb >>> 16) & 255) + 0.7152f * ((argb >>> 8) & 255) + 0.0722f * (argb & 255);
    }

    private static int channel(int argb, int c) {
        return (argb >>> (16 - 8 * c)) & 255;
    }
}
