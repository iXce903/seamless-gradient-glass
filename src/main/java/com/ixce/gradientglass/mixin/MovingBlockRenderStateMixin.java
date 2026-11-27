package com.ixce.gradientglass.mixin;

import com.ixce.gradientglass.MovingBlockLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// A moving block normally sees only itself. With a level set, it sees the blocks around it too.
@Mixin(MovingBlockRenderState.class)
abstract class MovingBlockRenderStateMixin implements MovingBlockLevel {
    @Shadow
    public BlockPos blockPos;
    @Unique
    private ClientLevel gradientglass$level;

    @Override
    public void gradientglass$setLevel(ClientLevel level) {
        gradientglass$level = level;
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void gradientglass$neighborState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (gradientglass$level != null && !pos.equals(blockPos)) cir.setReturnValue(gradientglass$level.getBlockState(pos));
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void gradientglass$neighborBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (gradientglass$level != null && !pos.equals(blockPos)) cir.setReturnValue(gradientglass$level.getBlockEntity(pos));
    }
}
