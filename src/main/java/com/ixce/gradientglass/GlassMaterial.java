package com.ixce.gradientglass;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

final class GlassMaterial {
    static final int NONE = -1, CLEAR = 0, WHITE = 1 + DyeColor.WHITE.ordinal();
    static final String[] NAMES = new String[DyeColor.values().length + 2];
    static final int TINTED = NAMES.length - 1;
    static final int PANES = TINTED;

    static {
        NAMES[CLEAR] = "glass";
        for (DyeColor dye : DyeColor.values()) NAMES[1 + dye.ordinal()] = dye.getSerializedName() + "_stained_glass";
        NAMES[TINTED] = "tinted_glass";
    }

    private GlassMaterial() {}

    static int of(BlockState state) {
        if (state.is(Blocks.GLASS) || state.is(Blocks.BEACON)) return CLEAR;
        if (state.is(Blocks.TINTED_GLASS)) return TINTED;
        if (state.getBlock() instanceof StainedGlassBlock glass) return 1 + glass.getColor().ordinal();
        return NONE;
    }

    static int ofPane(BlockState state) {
        if (state.is(Blocks.GLASS_PANE)) return CLEAR;
        if (state.getBlock() instanceof StainedGlassPaneBlock pane) return 1 + pane.getColor().ordinal();
        return NONE;
    }
}
