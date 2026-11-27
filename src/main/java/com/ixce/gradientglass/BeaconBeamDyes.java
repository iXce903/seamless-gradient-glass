package com.ixce.gradientglass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;

// Clear glass and panes inside a dyed beacon beam take the beam's color.
public final class BeaconBeamDyes {
    // Read by chunk meshing threads.
    private static final Map<Long, Integer> COLORS = new ConcurrentHashMap<>();
    private static final Map<Long, Map<Long, Integer>> BY_BEACON = new HashMap<>();

    private BeaconBeamDyes() {}

    /** The beam color at a position, or 0. */
    static int color(int x, int y, int z) {
        return COLORS.getOrDefault(BlockPos.asLong(x, y, z), 0);
    }

    public static void update(ClientLevel level, BlockPos beacon, List<BeaconBeamOwner.Section> sections) {
        Map<Long, Integer> dyed = new HashMap<>();
        var cursor = beacon.mutable();
        int y = beacon.getY();
        for (int i = 0; i < sections.size(); i++) {
            var section = sections.get(i);
            int color = section.getColor();
            int top = Math.min(y + section.getHeight(), level.getMaxY() + 1);
            // The first section is the undyed white beam.
            if (i > 0 && color != -1) {
                for (int cy = y; cy < top; cy++) {
                    var state = level.getBlockState(cursor.setY(cy));
                    if (state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE)) dyed.put(cursor.asLong(), 0xff000000 | color);
                }
            }
            y = top;
        }
        var previous = dyed.isEmpty() ? BY_BEACON.remove(beacon.asLong()) : BY_BEACON.put(beacon.asLong(), dyed);
        if (previous != null) {
            for (long pos : previous.keySet()) {
                if (!dyed.containsKey(pos) && COLORS.remove(pos) != null) GradientGlass.rebuildAround(level, BlockPos.of(pos));
            }
        }
        for (var entry : dyed.entrySet()) {
            if (!Objects.equals(COLORS.put(entry.getKey(), entry.getValue()), entry.getValue())) {
                GradientGlass.rebuildAround(level, BlockPos.of(entry.getKey()));
            }
        }
    }

    public static void remove(ClientLevel level, BlockPos beacon) {
        update(level, beacon, List.of());
    }

    static void clear() {
        COLORS.clear();
        BY_BEACON.clear();
    }
}
