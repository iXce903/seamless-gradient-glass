package com.ixce.gradientglass.mixin;

import com.ixce.gradientglass.BeaconBeamDyeCache;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BeamEmitter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconBlockEntity.class)
abstract class BeaconBlockEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private static void gradientglass$onTick(World world, BlockPos pos, BlockState state,
            BeaconBlockEntity blockEntity, CallbackInfo ci) {
        if (!world.isClient) return;
        List<BeamEmitter.BeamSegment> segments = blockEntity.getBeamSegments();
        if (segments.size() <= 1) {
            BeaconBeamDyeCache.clearBeacon(pos);
            return;
        }
        Map<Long, Integer> dyed = new HashMap<>();
        BlockPos.Mutable cursor = new BlockPos.Mutable(pos.getX(), pos.getY(), pos.getZ());
        int y = pos.getY();
        int maxY = world.getTopYInclusive() + 1;
        for (int i = 0; i < segments.size(); i++) {
            BeamEmitter.BeamSegment segment = segments.get(i);
            int height = segment.getHeight();
            int segColor = segment.getColor();
            // Segment 0 is the default un-dyed white beam (-1 / 0xFFFFFFFF); only dye for dyed segments.
            if (i > 0 && segColor != -1) {
                int argb = 0xff000000 | (segColor & 0xffffff);
                int top = Math.min(y + height, maxY);
                for (int cy = y; cy < top; cy++) {
                    cursor.setY(cy);
                    BlockState blockState = world.getBlockState(cursor);
                    if (blockState.isOf(Blocks.GLASS) || blockState.isOf(Blocks.GLASS_PANE)) {
                        dyed.put(cursor.asLong(), argb);
                    }
                }
            }
            y += height;
        }
        BeaconBeamDyeCache.updateBeacon(pos, dyed);
    }

    @Inject(method = "markRemoved", at = @At("HEAD"))
    private void gradientglass$onRemoved(CallbackInfo ci) {
        BeaconBlockEntity self = (BeaconBlockEntity) (Object) this;
        World world = self.getWorld();
        if (world != null && world.isClient) {
            BeaconBeamDyeCache.clearBeacon(self.getPos());
        }
    }
}
