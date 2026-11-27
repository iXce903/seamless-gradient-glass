package com.ixce.gradientglass.model;

import com.ixce.gradientglass.GlassMaterial;
import java.util.Arrays;

// Stained glass near stained glass with other patterns is drawn as a stack: its own texture at
// the bottom and one layer per other material above. Each layer's weight follows the distance
// to that material, so all of them meet evenly at a border, and the opacities are chosen from
// the top down so the stack is as opaque as a single texture.
final class PatternLayers {
    static final int MAX_OTHERS = 4;
    /** [0] is the block's own material. */
    final int[] materials = new int[MAX_OTHERS + 1];
    /** Mean opacity of each layer's texture. */
    final float[] alphas = new float[MAX_OTHERS + 1];
    final float[] opacity = new float[MAX_OTHERS + 1];
    int count;
    private final float[] weight = new float[MAX_OTHERS + 1];
    private final float[] nearest = new float[GlassMaterial.NAMES.length];

    /** Picks up to limit other materials nearest the box, in material order so every face stacks them alike. */
    void select(GlassFade patterns, int own, int limit, float x0, float y0, float z0, float x1, float y1, float z1) {
        Arrays.fill(nearest, Float.MAX_VALUE);
        for (int i = 0; i < patterns.size(); i++) {
            int material = patterns.material(i);
            nearest[material] = Math.min(nearest[material], patterns.gap(i, x0, y0, z0, x1, y1, z1));
        }
        materials[0] = own;
        count = 1;
        for (int picked = 0; picked < Math.min(limit, MAX_OTHERS); picked++) {
            int best = -1;
            for (int material = 0; material < nearest.length; material++) {
                if (nearest[material] < patterns.reach() && (best < 0 || nearest[material] < nearest[best])) best = material;
            }
            if (best < 0) break;
            materials[count++] = best;
            nearest[best] = Float.MAX_VALUE;
        }
        Arrays.sort(materials, 1, count);
    }

    void opacities(GlassFade patterns, float x, float y, float z) {
        float total = weight[0] = 1;
        for (int k = 1; k < count; k++) total += weight[k] = patterns.share(patterns.distance(x, y, z, materials[k]));
        float transmitted = 1;
        for (int k = count - 1; k >= 1; k--) {
            float w = weight[k] / total;
            opacity[k] = Math.min(1, w / transmitted);
            transmitted -= w * alphas[k];
        }
        opacity[0] = Math.min(1, weight[0] / total / transmitted);
    }
}
