package com.ixce.gradientglass.texture;

import com.ixce.gradientglass.GlassColor;
import net.minecraft.client.resources.model.sprite.Material;

// alpha is the texture's mean opacity.
public record GlassTexture(Material.Baked material, boolean clear, float[] gain, float alpha) {
    public int shade(float[] lab, float opacity) {
        if (clear) return Math.round(255 * opacity) << 24 | 0xffffff;
        int gradient = GlassColor.argb(lab[0], lab[1], lab[2], 255 * opacity);
        if (gain == null) return gradient | 0xffffff;
        return gradient & 0xff000000
                | scale((gradient >>> 16) & 255, gain[0]) << 16
                | scale((gradient >>> 8) & 255, gain[1]) << 8
                | scale(gradient & 255, gain[2]);
    }

    private static int scale(int channel, float gain) {
        return Math.min(255, Math.round(channel * gain));
    }
}
