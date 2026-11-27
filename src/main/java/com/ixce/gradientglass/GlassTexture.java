package com.ixce.gradientglass;

import net.minecraft.client.resources.model.sprite.Material;

record GlassTexture(Material.Baked material, boolean clear, float[] gain) {
    int tint(int gradient) {
        if (clear) return 0xffffffff;
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
