package com.ixce.gradientglass;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// {L, a, b, alpha} in OKLab (Ottosson 2020).
final class GlassColor {
    private static final float[] SRGB_TO_LINEAR = new float[256];
    private static final int LINEAR_STEPS = 1 << 14;
    private static final int[] LINEAR_TO_SRGB = new int[LINEAR_STEPS + 1];
    private static final Map<Integer, float[]> LAB = new ConcurrentHashMap<>();
    static final float[] TRANSPARENT = {1, 0, 0, 0};

    static {
        for (int i = 0; i < SRGB_TO_LINEAR.length; i++) {
            double s = i / 255.0;
            SRGB_TO_LINEAR[i] = (float) (s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4));
        }
        for (int i = 0; i < LINEAR_TO_SRGB.length; i++) {
            double l = (double) i / LINEAR_STEPS;
            double s = l <= 0.0031308 ? 12.92 * l : 1.055 * Math.pow(l, 1.0 / 2.4) - 0.055;
            LINEAR_TO_SRGB[i] = Math.min(255, Math.max(0, (int) Math.round(s * 255.0)));
        }
    }

    private GlassColor() {}

    // Cached and shared: callers must not modify it.
    static float[] lab(int argb) {
        return LAB.computeIfAbsent(argb, GlassColor::toLab);
    }

    private static float[] toLab(int argb) {
        float r = SRGB_TO_LINEAR[(argb >>> 16) & 255], g = SRGB_TO_LINEAR[(argb >>> 8) & 255], b = SRGB_TO_LINEAR[argb & 255];
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new float[] {
                (float) (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s),
                (float) (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s),
                (float) (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s),
                argb >>> 24};
    }

    static int argb(float[] c) {
        return argb(c[0], c[1], c[2], c[3]);
    }

    static int argb(float lightness, float a, float b, float alpha) {
        float l = lightness + 0.3963377774f * a + 0.2158037573f * b;
        float m = lightness - 0.1055613458f * a - 0.0638541728f * b;
        float s = lightness - 0.0894841775f * a - 1.2914855480f * b;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        return Math.min(255, Math.max(0, Math.round(alpha))) << 24
                | channel(4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s) << 16
                | channel(-1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s) << 8
                | channel(-0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s);
    }

    private static int channel(float linear) {
        return LINEAR_TO_SRGB[Math.min(LINEAR_STEPS, Math.max(0, Math.round(linear * LINEAR_STEPS)))];
    }

    static void bernstein(float t, float[] out) {
        float u = 1 - t;
        out[0] = u * u;
        out[1] = 2 * u * t;
        out[2] = t * t;
    }

    // Opacity takes the minimum, so stained glass is fully transparent where it meets clear glass.
    // Equal inputs give an exact copy, so uniform faces still merge.
    static final class Mix {
        private final float[] sum = new float[3];
        private float weight, opacity;
        private float[] first;
        private boolean uniform;

        Mix clear() {
            Arrays.fill(sum, 0);
            weight = 0;
            opacity = 255;
            first = null;
            uniform = true;
            return this;
        }

        Mix add(float[] color, float w) {
            if (color == null) return this;
            if (first == null) first = color;
            else if (uniform && color != first && !Arrays.equals(first, color)) uniform = false;
            for (int i = 0; i < 3; i++) sum[i] += w * color[i];
            weight += w;
            opacity = Math.min(opacity, color[3]);
            return this;
        }

        Mix add(float[] color) {
            return add(color, 1);
        }

        boolean empty() {
            return first == null;
        }

        float[] into(float[] out) {
            if (uniform) System.arraycopy(first, 0, out, 0, 4);
            else {
                for (int i = 0; i < 3; i++) out[i] = sum[i] / weight;
                out[3] = opacity;
            }
            return out;
        }
    }
}
