package com.ixce.gradientglass.mixin;

import com.ixce.gradientglass.GradientGlass;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("TAIL"))
    private void gradientglass$rebuildBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        rebuildGradient(pos, oldState, newState);
    }

    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void gradientglass$rebuildDirtyBlock(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        rebuildGradient(pos, oldState, newState);
    }

    @Unique
    private void rebuildGradient(BlockPos pos, BlockState oldState, BlockState newState) {
        // A moving piston block may be glass on its way.
        if (GradientGlass.isGlass(oldState) || GradientGlass.isGlass(newState) || oldState.is(Blocks.MOVING_PISTON) || newState.is(Blocks.MOVING_PISTON)) {
            GradientGlass.rebuildAround((ClientLevel) (Object) this, pos);
        }
    }
}
