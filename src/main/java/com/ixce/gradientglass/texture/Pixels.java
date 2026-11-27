package com.ixce.gradientglass.texture;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

record Pixels(int size, int[] argb) {
    static Pixels read(Resource resource) throws IOException {
        try (var stream = resource.open(); var image = NativeImage.read(stream)) {
            int size = image.getWidth(), height = image.getHeight();
            if (size == 0 || height % size != 0) return null;
            return new Pixels(size, Arrays.copyOf(image.getPixels(), size * size));
        }
    }

    SpriteContents sprite(Identifier id) {
        return withBorders(id, 15);
    }

    SpriteContents withBorders(Identifier id, int edges) {
        int border = 1;
        var image = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int sx = x, sy = y;
                if ((edges & 1) == 0 && x < border) sx = border;
                if ((edges & 2) == 0 && x >= size - border) sx = size - border - 1;
                if ((edges & 8) == 0 && y < border) sy = border;
                if ((edges & 4) == 0 && y >= size - border) sy = size - border - 1;
                image.setPixel(x, y, argb[sy * size + sx]);
            }
        }
        return new SpriteContents(id, new FrameSize(size, size), image);
    }
}
