package com.ixce.gradientglass.mixin;

//? if >=1.21.11 {
/*import com.ixce.gradientglass.GradientGlass;
import com.ixce.gradientglass.MovingBlockLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Only glass gets to see its neighbors while moving; other blocks would cull faces against them.
@Mixin(PistonHeadRenderer.class)
abstract class PistonHeadRendererMixin {
    @Inject(method = "createMovingBlock", at = @At("RETURN"))
    private static void gradientglass$seeNeighbors(BlockPos pos, BlockState state, Holder<Biome> biome, Level level,
                                                   CallbackInfoReturnable<MovingBlockRenderState> cir) {
        if (level instanceof ClientLevel client && GradientGlass.isGlass(state)) {
            ((MovingBlockLevel) cir.getReturnValue()).gradientglass$setLevel(client);
        }
    }
}
*///?}
