package com.ixce.gradientglass.model;

import com.ixce.gradientglass.BeaconBeamDyes;
import com.ixce.gradientglass.GlassColor;
import com.ixce.gradientglass.GlassMaterial;
import com.ixce.gradientglass.config.GlassConfig;
import com.ixce.gradientglass.texture.GlassTextures;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Glass blocks and panes up to two blocks around the one being built, by offset. Blocks and
// panes blend, connect and fade alike, so both models read their neighbors from here.
final class GlassNeighborhood {
    private static final Direction[] DIRECTIONS = Direction.values();
    // The 3x3x3 kernel offsets nearest first, so each one's inner neighbors are reached before it.
    private static final int[][] KERNEL = new int[26][];
    // The steps that lead into each kernel offset from a nearer one.
    private static final Direction[][] INTO = new Direction[26][];

    static {
        int k = 0;
        for (int steps = 1; steps <= 3; steps++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y <= 1; y++) {
                    for (int x = -1; x <= 1; x++) {
                        if (Math.abs(x) + Math.abs(y) + Math.abs(z) != steps) continue;
                        KERNEL[k] = new int[] {x, y, z};
                        var into = new ArrayList<Direction>();
                        if (x != 0) into.add(x > 0 ? Direction.EAST : Direction.WEST);
                        if (y != 0) into.add(y > 0 ? Direction.UP : Direction.DOWN);
                        if (z != 0) into.add(z > 0 ? Direction.SOUTH : Direction.NORTH);
                        INTO[k++] = into.toArray(Direction[]::new);
                    }
                }
            }
        }
    }

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private BlockAndTintGetter view;
    private BlockPos pos;
    private GlassTextures textures;
    private final BlockState[] states = new BlockState[125];
    private final int[] materials = new int[125];
    private final boolean[] panes = new boolean[125];
    // Clear glass in a dyed beacon beam counts as white stained glass of the beam's color.
    private final float[][] dyes = new float[125][];
    private final float[][] blurred = new float[125][4];
    private final float[][] blurredRef = new float[125][];
    private final int[] loaded = new int[125], blurredStamp = new int[125], visited = new int[125];
    private final int[] queue = new int[125];
    private final boolean[] reached = new boolean[27];
    private int stamp;
    private final GlassColor.Mix blur = new GlassColor.Mix();
    // Clear glass connected to stained glass, or the other way round.
    final GlassFade fade = new GlassFade();
    // Stained glass connected to stained glass with another pattern.
    final GlassFade patterns = new GlassFade();

    void begin(BlockAndTintGetter view, BlockPos pos, GlassTextures textures) {
        this.view = view;
        this.pos = pos;
        this.textures = textures;
        if (++stamp == 0) {
            Arrays.fill(loaded, 0);
            Arrays.fill(blurredStamp, 0);
            Arrays.fill(visited, 0);
            stamp = 1;
        }
        int self = material(0, 0, 0);
        boolean clear = self == GlassMaterial.CLEAR;
        boolean blendPatterns = !clear && GlassConfig.current.patternLayers > 0;
        // Only glass connected to this block takes part in the transition.
        fade.clear();
        patterns.clear();
        int head = 0, tail = 0;
        queue[tail++] = index(0, 0, 0);
        visited[index(0, 0, 0)] = stamp;
        while (head < tail) {
            int cell = queue[head++];
            int dx = cell % 5 - 2, dy = cell / 5 % 5 - 2, dz = cell / 25 - 2;
            int other = materials[cell];
            if ((other == GlassMaterial.CLEAR) != clear) fade.add(dx, dy, dz, other);
            else if (blendPatterns && other != self && !textures.samePattern(self, other)) patterns.add(dx, dy, dz, other);
            for (Direction direction : DIRECTIONS) {
                if (!joined(dx, dy, dz, direction)) continue;
                int next = index(dx + direction.getStepX(), dy + direction.getStepY(), dz + direction.getStepZ());
                if (visited[next] == stamp) continue;
                visited[next] = stamp;
                queue[tail++] = next;
            }
        }
    }

    private static int index(int dx, int dy, int dz) {
        return (dx + 2) + 5 * (dy + 2) + 25 * (dz + 2);
    }

    private static boolean inside(int dx, int dy, int dz) {
        return Math.abs(dx) <= 2 && Math.abs(dy) <= 2 && Math.abs(dz) <= 2;
    }

    private int load(int dx, int dy, int dz) {
        int index = index(dx, dy, dz);
        if (loaded[index] == stamp) return index;
        BlockState state = view.getBlockState(cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
        // A block being pushed in already counts as the block it will become.
        if (state.is(Blocks.MOVING_PISTON) && view.getBlockEntity(cursor) instanceof PistonMovingBlockEntity moving && !moving.isSourcePiston()) {
            state = moving.getMovedState();
        }
        int material = GlassMaterial.of(state);
        boolean pane = material == GlassMaterial.NONE && (material = GlassMaterial.ofPane(state)) != GlassMaterial.NONE;
        int dye = material == GlassMaterial.CLEAR ? BeaconBeamDyes.color(cursor.getX(), cursor.getY(), cursor.getZ()) : 0;
        states[index] = state;
        materials[index] = dye != 0 ? GlassMaterial.WHITE : material;
        panes[index] = pane;
        dyes[index] = dye != 0 ? GlassColor.lab(dye) : null;
        loaded[index] = stamp;
        return index;
    }

    BlockState state(int dx, int dy, int dz) {
        return states[load(dx, dy, dz)];
    }

    /** The material of a glass block or pane, or NONE. */
    int material(int dx, int dy, int dz) {
        return materials[load(dx, dy, dz)];
    }

    int block(int dx, int dy, int dz) {
        int index = load(dx, dy, dz);
        return panes[index] ? GlassMaterial.NONE : materials[index];
    }

    static boolean hasArm(BlockState pane, Direction arm) {
        return switch (arm) {
            case NORTH -> pane.getValue(CrossCollisionBlock.NORTH);
            case SOUTH -> pane.getValue(CrossCollisionBlock.SOUTH);
            case WEST -> pane.getValue(CrossCollisionBlock.WEST);
            case EAST -> pane.getValue(CrossCollisionBlock.EAST);
            default -> false;
        };
    }

    // Glass blocks touch through whole faces. A pane only touches what its arms reach, and what is stacked on it.
    boolean joined(int dx, int dy, int dz, Direction direction) {
        int nx = dx + direction.getStepX(), ny = dy + direction.getStepY(), nz = dz + direction.getStepZ();
        if (!inside(nx, ny, nz)) return false;
        int a = load(dx, dy, dz), b = load(nx, ny, nz);
        if (materials[a] == GlassMaterial.NONE || materials[b] == GlassMaterial.NONE) return false;
        if (direction.getAxis() == Direction.Axis.Y) return true;
        return (!panes[a] || hasArm(states[a], direction)) && (!panes[b] || hasArm(states[b], direction.getOpposite()));
    }

    private float[] color(int index) {
        int material = materials[index];
        if (material <= GlassMaterial.CLEAR) return null;
        return dyes[index] != null ? dyes[index] : textures.color(material);
    }

    // A glass cell's color blurred 1-2-1 on every axis over the glass connected to it within one
    // block. The kernel only depends on the cell, so every face and pane reading it agrees.
    // Null for a cell that is not glass, or has no stained glass around it.
    float[] blurred(int dx, int dy, int dz) {
        int index = load(dx, dy, dz);
        if (blurredStamp[index] == stamp) return blurredRef[index];
        float[] result = null;
        if (materials[index] != GlassMaterial.NONE) {
            blur.clear();
            blur.add(color(index), 8);
            if (GlassConfig.current.blur) {
                Arrays.fill(reached, false);
                reached[13] = true;
                for (int k = 0; k < KERNEL.length; k++) {
                    int[] e = KERNEL[k];
                    boolean reach = false;
                    for (Direction direction : INTO[k]) {
                        int px = e[0] - direction.getStepX(), py = e[1] - direction.getStepY(), pz = e[2] - direction.getStepZ();
                        if (reached[13 + px + 3 * py + 9 * pz] && joined(dx + px, dy + py, dz + pz, direction)) {
                            reach = true;
                            break;
                        }
                    }
                    if (!reach) continue;
                    reached[13 + e[0] + 3 * e[1] + 9 * e[2]] = true;
                    blur.add(color(load(dx + e[0], dy + e[1], dz + e[2])), 1 << (3 - Math.abs(e[0]) - Math.abs(e[1]) - Math.abs(e[2])));
                }
            }
            if (!blur.empty()) result = blur.into(blurred[index]);
        }
        blurredStamp[index] = stamp;
        blurredRef[index] = result;
        return result;
    }
}
