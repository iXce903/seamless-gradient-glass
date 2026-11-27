package com.ixce.gradientglass;

import java.util.List;

final class TextureNormalizer {
    // Pixels below this alpha can carry arbitrary color and do not set the maximum.
    private static final int VISIBLE_ALPHA = 32;
    // A channel darker than this fraction of the brightest one borrows the luminance pattern,
    // otherwise tinting toward that channel's hue would show its noise.
    private static final float WEAK_CHANNEL = 0.2f;

    record Tint(int material, int average, float[] gain) {}

    private final float[] max = new float[3];
    private final boolean[] borrow = new boolean[3];
    private float maxLuminance;
    private final int average;
    private final float[] gain = new float[3];

    TextureNormalizer(List<Pixels> images) {
        for (var image : images) {
            for (int pixel : image.argb()) {
                if ((pixel >>> 24) < VISIBLE_ALPHA) continue;
                for (int c = 0; c < 3; c++) max[c] = Math.max(max[c], channel(pixel, c));
                maxLuminance = Math.max(maxLuminance, luminance(pixel));
            }
        }
        float top = Math.max(max[0], Math.max(max[1], max[2]));
        for (int c = 0; c < 3; c++) borrow[c] = max[c] <= 0 || max[c] < WEAK_CHANNEL * top;
        double weight = 0;
        double[] sum = new double[3], normalized = new double[3];
        for (var image : images) {
            for (int pixel : image.argb()) {
                int alpha = pixel >>> 24;
                weight += alpha;
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
    }

    int average() {
        return average;
    }

    Tint tint(int material) {
        return new Tint(material, average, gain.clone());
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
