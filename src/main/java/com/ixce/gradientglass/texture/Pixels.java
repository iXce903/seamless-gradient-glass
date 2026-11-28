package com.ixce.gradientglass.texture;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
//?}
import net.minecraft.server.packs.resources.Resource;

record Pixels(int size, int[] argb) {
    static Pixels read(Resource resource) throws IOException {
        try (var stream = resource.open(); var image = NativeImage.read(stream)) {
            int size = image.getWidth(), height = image.getHeight();
            if (size == 0 || height % size != 0) return null;
            //? if >=1.21.2 {
            return new Pixels(size, Arrays.copyOf(image.getPixels(), size * size));
            //?} else {
            /*int[] argb = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) argb[y * size + x] = swapRedBlue(image.getPixelRGBA(x, y));
            }
            return new Pixels(size, argb);
            *///?}
        }
    }

    static void setPixel(NativeImage image, int x, int y, int argb) {
        //? if >=1.21.2 {
        image.setPixel(x, y, argb);
        //?} else {
        /*image.setPixelRGBA(x, y, swapRedBlue(argb));
        *///?}
    }

    //? if <1.21.2 {
    /*private static int swapRedBlue(int color) {
        return (color & 0xff00ff00) | ((color & 0xff) << 16) | ((color >>> 16) & 0xff);
    }
    *///?}

    //? if >=1.21.11 {
    /*SpriteContents sprite(Identifier id) {
        return withBorders(id, 15);
    }

    SpriteContents withBorders(Identifier id, int edges) {
    *///?} else {
    SpriteContents sprite(ResourceLocation id) {
        return withBorders(id, 15);
    }

    SpriteContents withBorders(ResourceLocation id, int edges) {
    //?}
        int border = 1;
        var image = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int sx = x, sy = y;
                if ((edges & 1) == 0 && x < border) sx = border;
                if ((edges & 2) == 0 && x >= size - border) sx = size - border - 1;
                if ((edges & 8) == 0 && y < border) sy = border;
                if ((edges & 4) == 0 && y >= size - border) sy = size - border - 1;
                setPixel(image, x, y, argb[sy * size + sx]);
            }
        }
        //? if >=1.21.11 {
        /*return new SpriteContents(id, new FrameSize(size, size), image);
        *///?} else {
        return new SpriteContents(id, new FrameSize(size, size), image, ResourceMetadata.EMPTY);
        //?}
    }
}
