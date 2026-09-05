package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Broken masonry stays beside the walking lanes instead of filling their intersections. */
public final class MazeRuins {
    private MazeRuins() {}

    public static void column(ChunkAccess chunk, long seed, int x, int z, int floor,
                              int lx, int lz, int low, int high) {
        int center = (low + high) / 2;
        long roll = CatacombLayout.hash(seed, Math.floorDiv(x - lx, 13), Math.floorDiv(z - lz, 13));
        boolean turn = (roll & 1) == 0;
        int along = turn ? lz : lx, across = turn ? lx : lz;
        boolean wall = (across == low && along >= low && along <= high)
                || (along == high && across >= low && across <= center - 2);
        boolean door = Math.abs(along - center) <= 1;
        if (wall && !door) {
            int height = 2 + (int)Math.floorMod(roll >>> 12, 3)
                    + (int)Math.round(Math.sin(along * .8 + (roll & 15)) * 1.5);
            for (int y = 1; y <= height; y++) {
                var state = (y == 1 ? Asterion.SHALE_BRICKS : Asterion.ANCIENT_BRICKS).defaultBlockState();
                if (y == height) state = Asterion.ANCIENT_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, turn ? Direction.WEST : Direction.NORTH);
                chunk.setBlockState(new BlockPos(x, floor + y, z), state, 0);
            }
        } else if (along >= low && along <= high && across == low + 1 && !door
                && Math.floorMod(CatacombLayout.hash(seed, x, z), 3) == 0) {
            chunk.setBlockState(new BlockPos(x, floor + 1, z), Asterion.SHALE_BRICK_SLAB.defaultBlockState(), 0);
        }
    }
}
