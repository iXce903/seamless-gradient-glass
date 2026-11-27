package com.ixce.gradientglass.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
//? if >=1.21.4 {
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//?} else {
/*import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
*///?}

@Mixin(SpriteSources.class)
public interface SpriteSourcesAccessor {
    //? if >=1.21.4 {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<ResourceLocation, MapCodec<? extends SpriteSource>> gradientglass$idMapper() {
        throw new AssertionError();
    }
    //?} else {
    /*@Invoker("register")
    static SpriteSourceType gradientglass$register(String name, MapCodec<? extends SpriteSource> codec) {
        throw new AssertionError();
    }
    *///?}
}
