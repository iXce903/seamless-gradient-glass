package com.ixce.gradientglass;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * Client-side cache mapping vanilla clear glass / glass pane positions hit by a dyed beacon beam
 * to their dynamic ARGB dye color.
 */
public final class BeaconBeamDyeCache {
    // Thread-safe map read by chunk meshing worker threads.
    private static final ConcurrentHashMap<Long, Integer> DYED_COLORS = new ConcurrentHashMap<>();
    // Client-thread index tracking which glass positions each beacon currently dyes.
    private static final Map<Long, Map<Long, Integer>> BEACON_POSITIONS = new HashMap<>();

    private BeaconBeamDyeCache() {}

    /**
     * Returns the cached ARGB color (alpha 0xFF) for the given position, or 0 if not dyed by a beacon.
     */
    public static int getColor(BlockPos pos) {
        return DYED_COLORS.getOrDefault(pos.asLong(), 0);
    }

    /**
     * Writes a dyed color for a single position and schedules a re-render only if the color changed.
     */
    public static void put(BlockPos pos, int argb) {
        long key = pos.asLong();
        Integer previous = DYED_COLORS.put(key, argb);
        if (!Objects.equals(previous, argb)) {
            scheduleBlockRender(key);
        }
    }

    /**
     * Removes a dyed position and schedules a re-render only if it was previously cached.
     */
    public static void remove(BlockPos pos) {
        long key = pos.asLong();
        if (DYED_COLORS.remove(key) != null) {
            scheduleBlockRender(key);
        }
    }

    /**
     * Reconciles the set of glass positions dyed by a specific beacon, triggering chunk re-renders
     * only for positions whose color was added, changed, or removed.
     */
    public static void updateBeacon(BlockPos beaconPos, Map<Long, Integer> nextColors) {
        long beaconKey = beaconPos.asLong();
        Map<Long, Integer> previous = BEACON_POSITIONS.get(beaconKey);
        if (nextColors.isEmpty()) {
            if (previous == null || previous.isEmpty()) return;
            BEACON_POSITIONS.remove(beaconKey);
            for (long posKey : previous.keySet()) {
                if (DYED_COLORS.remove(posKey) != null) {
                    scheduleBlockRender(posKey);
                }
            }
            return;
        }
        if (previous != null && previous.equals(nextColors)) {
            return;
        }
        if (previous != null) {
            for (long oldPosKey : previous.keySet()) {
                if (!nextColors.containsKey(oldPosKey) && DYED_COLORS.remove(oldPosKey) != null) {
                    scheduleBlockRender(oldPosKey);
                }
            }
        }
        for (Map.Entry<Long, Integer> entry : nextColors.entrySet()) {
            long posKey = entry.getKey();
            int color = entry.getValue();
            if (!Objects.equals(DYED_COLORS.put(posKey, color), color)) {
                scheduleBlockRender(posKey);
            }
        }
        BEACON_POSITIONS.put(beaconKey, new HashMap<>(nextColors));
    }

    public static void clearBeacon(BlockPos beaconPos) {
        updateBeacon(beaconPos, Map.of());
    }

    public static void clearAll() {
        DYED_COLORS.clear();
        BEACON_POSITIONS.clear();
    }

    private static void scheduleBlockRender(long posKey) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.worldRenderer == null) return;
        int x = BlockPos.unpackLongX(posKey);
        int y = BlockPos.unpackLongY(posKey);
        int z = BlockPos.unpackLongZ(posKey);
        client.worldRenderer.scheduleBlockRenders(x, y, z, x, y, z);
    }
}
