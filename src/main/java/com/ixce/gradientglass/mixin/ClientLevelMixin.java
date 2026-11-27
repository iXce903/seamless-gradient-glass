package com.ixce.gradientglass.mixin;

import com.ixce.gradientglass.GradientGlass;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla rebuilds only the sections within one block of a change; glass gradients reach further.
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
        if (!GradientGlass.isGlass(oldState) && !GradientGlass.isGlass(newState)) return;
        int reach = GradientGlass.REACH;
        ((ClientLevel) (Object) this).setSectionRangeDirty(
                SectionPos.blockToSectionCoord(pos.getX() - reach),
                SectionPos.blockToSectionCoord(pos.getY() - reach),
                SectionPos.blockToSectionCoord(pos.getZ() - reach),
                SectionPos.blockToSectionCoord(pos.getX() + reach),
                SectionPos.blockToSectionCoord(pos.getY() + reach),
                SectionPos.blockToSectionCoord(pos.getZ() + reach));
    }
}
