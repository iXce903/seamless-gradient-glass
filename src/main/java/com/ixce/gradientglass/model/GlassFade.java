package com.ixce.gradientglass.model;

import com.ixce.gradientglass.config.GlassConfig;

// Distances to connected glass of the other kind (stained or clear), in block coordinates.
// Near the border both textures are drawn: clear glass underneath, a stained haze on top.
final class GlassFade {
    // Lifts the stained layer off the clear one, so it is always drawn on top.
    static final float LAYER_GAP = 1f / 512;
    private final float[] blocks = new float[3 * 125];
    private final int[] materials = new int[125];
    private int count;
    // Width in blocks of the transition on each side of the border.
    private float width;

    void clear() {
        count = 0;
        width = GlassConfig.current.transitionWidth;
    }

    void add(int dx, int dy, int dz, int material) {
        if (gap(dx, 0, 1) + gap(dy, 0, 1) + gap(dz, 0, 1) >= width * width) return;
        blocks[3 * count] = dx;
        blocks[3 * count + 1] = dy;
        blocks[3 * count + 2] = dz;
        materials[count] = material;
        count++;
    }

    boolean reaches(float x0, float y0, float z0, float x1, float y1, float z1) {
        for (int i = 0; i < count; i++) {
            if (gap(blocks[3 * i], x0, x1) + gap(blocks[3 * i + 1], y0, y1) + gap(blocks[3 * i + 2], z0, z1) < width * width) return true;
        }
        return false;
    }

    float distance(float x, float y, float z) {
        return (float) Math.sqrt(squared(nearest(x, y, z), x, y, z));
    }

    int nearestMaterial(float x, float y, float z) {
        return materials[nearest(x, y, z)];
    }

    private int nearest(float x, float y, float z) {
        int nearest = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            float d = squared(i, x, y, z);
            if (d < best) {
                best = d;
                nearest = i;
            }
        }
        return nearest;
    }

    int size() {
        return count;
    }

    int material(int i) {
        return materials[i];
    }

    /** Squared distance from block i to the box. */
    float gap(int i, float x0, float y0, float z0, float x1, float y1, float z1) {
        return gap(blocks[3 * i], x0, x1) + gap(blocks[3 * i + 1], y0, y1) + gap(blocks[3 * i + 2], z0, z1);
    }

    /** The squared distance within which blocks count. */
    float reach() {
        return width * width;
    }

    float distance(float x, float y, float z, int material) {
        float best = width * width;
        for (int i = 0; i < count; i++) if (materials[i] == material) best = Math.min(best, squared(i, x, y, z));
        return (float) Math.sqrt(best);
    }

    /** A material's pull at a distance: 1 on it, falling with zero slope at both ends to 0 at the width. */
    float share(float distance) {
        return 1 - smoothstep(distance / width);
    }

    private float squared(int i, float x, float y, float z) {
        return gap(blocks[3 * i], x, x) + gap(blocks[3 * i + 1], y, y) + gap(blocks[3 * i + 2], z, z);
    }

    /** Opacity of the stained layer at a signed distance from the border, positive inside stained glass. */
    float stained(float signedDistance) {
        return smoothstep((signedDistance + width) / (2 * width));
    }

    /** Opacity of the clear layer under stained glass at a distance from the border. */
    float clearUnder(float distance) {
        return 1 - smoothstep(distance / width);
    }

    private static float smoothstep(float t) {
        t = Math.min(1, Math.max(0, t));
        return t * t * (3 - 2 * t);
    }

    // Squared gap along one axis between the block [min, min + 1] and the span [lo, hi].
    private static float gap(float min, float lo, float hi) {
        float d = Math.max(0, Math.max(min - hi, lo - (min + 1)));
        return d * d;
    }
}
