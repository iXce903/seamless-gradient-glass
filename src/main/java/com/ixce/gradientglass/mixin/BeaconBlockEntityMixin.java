package com.ixce.gradientglass.mixin;

import com.ixce.gradientglass.BeaconBeamDyes;
import com.ixce.gradientglass.config.GlassConfig;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The beacon replaces its section list after each full scan of the beam, so the dyed glass
// only needs to be found again when the list changes.
@Mixin(BeaconBlockEntity.class)
abstract class BeaconBlockEntityMixin {
    @Unique
    private List<BeaconBeamOwner.Section> gradientglass$dyedSections;

    @Inject(method = "tick", at = @At("TAIL"))
    private static void gradientglass$dye(Level level, BlockPos pos, BlockState state, BeaconBlockEntity beacon, CallbackInfo ci) {
        if (!(level instanceof ClientLevel client)) return;
        var self = (BeaconBlockEntityMixin) (Object) beacon;
        var sections = GlassConfig.beaconDye() ? beacon.getBeamSections() : List.<BeaconBeamOwner.Section>of();
        if (sections == self.gradientglass$dyedSections) return;
        self.gradientglass$dyedSections = sections;
        BeaconBeamDyes.update(client, pos, sections);
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void gradientglass$undye(CallbackInfo ci) {
        var beacon = (BeaconBlockEntity) (Object) this;
        if (beacon.getLevel() instanceof ClientLevel client) BeaconBeamDyes.remove(client, beacon.getBlockPos());
    }
}
