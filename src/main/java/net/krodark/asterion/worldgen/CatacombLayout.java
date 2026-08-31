package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Stateless, chunk-order-independent undercroft. The surface maze continues infinitely. */
public final class CatacombLayout {
    public static final int TILE = 32;
    public static final int FLOOR_Y = 6;
    public static final int WATER_Y = 7;
    public static final int ROOF_Y = 16;

    private CatacombLayout() { }

    public static boolean passage(int x, int z) {
        int lx = Math.floorMod(x, TILE), lz = Math.floorMod(z, TILE);
        // Outer galleries keep the network connected even when a crypt's gate is shut.
        return lx <= 2 || lx >= 30 || lz <= 2 || lz >= 30
                || Math.abs(lx - 16) <= 2 || Math.abs(lz - 16) <= 2;
    }

    public static int floor(long seed, int x, int z) {
        // One common water surface prevents flow from draining the deeper trenches.
        return FLOOR_Y - (Math.floorMod((x >> 3) * 13L + (z >> 3) * 7L + seed, 4) == 0 ? 1 : 0);
    }

    public static void generate(ChunkAccess chunk, long seed) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                boolean open = passage(x, z);
                int floor = floor(seed, x, z);
                for (int y = 3; y <= ROOF_Y; y++) {
                    BlockState state;
                    if (!open || y <= floor || y >= 14) {
                        state = Math.floorMod(x * 17L + y * 3L + z * 31L + seed, 9) < 3
                                ? Asterion.ANCIENT_MOSSY_BRICKS.defaultBlockState()
                                : Asterion.ANCIENT_BRICKS.defaultBlockState();
                    } else {
                        state = (y <= WATER_Y ? net.krodark.asterion.fluid.HeavyWater.WATER_BLOCK : Blocks.AIR).defaultBlockState();
                    }
                    chunk.setBlockState(cursor.set(x, y, z), state, 0);
                }
                if (open && Math.floorMod(x * 31L + z * 17L + seed, 43) == 0) {
                    chunk.setBlockState(cursor.set(x, 14, z), Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 0);
                    chunk.setBlockState(cursor.set(x, 15, z), Blocks.WATER.defaultBlockState(), 0);
                    chunk.setBlockState(cursor.set(x, 13, z), Blocks.POINTED_DRIPSTONE.defaultBlockState()
                            .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN)
                            .setValue(BlockStateProperties.DRIPSTONE_THICKNESS, DripstoneThickness.TIP), 0);
                } else if (open && Math.floorMod(x * 7L + z * 11L, 67) == 0) {
                    chunk.setBlockState(cursor.set(x, 13, z), Blocks.SOUL_LANTERN.defaultBlockState()
                            .setValue(BlockStateProperties.HANGING, true), 0);
                }
            }
        }
    }
}
