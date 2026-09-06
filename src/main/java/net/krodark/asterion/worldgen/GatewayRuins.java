package net.krodark.asterion.worldgen;

import java.util.Arrays;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/** A supported, weathered shrine around the original open descent shaft. */
public final class GatewayRuins {
    private GatewayRuins() {}

    public static int surface(ServerLevel level, int x, int z) {
        int[] heights = new int[25];
        int index = 0;
        for (int dx = -6; dx <= 6; dx += 3) for (int dz = -6; dz <= 6; dz += 3)
            heights[index++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
        Arrays.sort(heights);
        return Math.min(level.getMaxY() - 12, heights[heights.length / 2]);
    }

    public static void build(ServerLevel level, int x, int y, int z) {
        build(level, x, y, z, 3);
    }

    public static void build(ServerLevel level, int x, int y, int z, int rim) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            int edge = Math.max(Math.abs(dx), Math.abs(dz));
            if (Math.abs(dx) + Math.abs(dz) > 13 || edge < rim) continue;
            long hash = CatacombLayout.hash(level.getSeed(), x + dx, z + dz);
            boolean path = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
            Block paving = edge == rim || edge == 7 ? Asterion.SHADED_SHALE_BRICKS
                    : path ? Asterion.ANCIENT_STONE
                    : edge == 8 && Math.floorMod(hash, 5) == 0 ? Asterion.ANCIENT_MOSS
                    : edge >= 7 ? Asterion.SHALE_BRICKS : Asterion.ANCIENT_BRICKS;
            level.setBlock(pos.set(x + dx, y - 1, z + dz), paving.defaultBlockState(), 2);
            // Continue the footing through air and water until it meets solid ground.
            for (int support = y - 2; support > level.getMinY(); support--) {
                pos.set(x + dx, support, z + dz);
                if (level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)
                        && level.getFluidState(pos).isEmpty()) break;
                level.setBlock(pos, Asterion.SHADED_SHALE.defaultBlockState(), 2);
            }
            if (edge == rim) {
                boolean entry = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
                level.setBlock(pos.set(x + dx, y, z + dz), (entry ? Asterion.SHADED_SHALE_BRICK_SLAB
                        : Asterion.SHADED_SHALE_BRICK_WALL).defaultBlockState(), 2);
            } else if (edge == 8 && (Math.abs(dx) <= 2 || Math.abs(dz) <= 2)) {
                Direction face = dx == 8 ? Direction.WEST : dx == -8 ? Direction.EAST
                        : dz == 8 ? Direction.NORTH : Direction.SOUTH;
                level.setBlock(pos.set(x + dx, y - 1, z + dz), Asterion.ANCIENT_STONE_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, face), 2);
            } else if (edge == 8 && Math.floorMod(hash, 4) != 0) {
                level.setBlock(pos.set(x + dx, y, z + dz), Asterion.SHADED_SHALE_BRICK_SLAB.defaultBlockState(), 2);
            }
        }
        // One surviving arch and a broken return wall leave the well visible from the approach.
        for (int side : new int[]{-1, 1}) {
            for (int rise = 0; rise < 6; rise++) for (int depth = 0; depth < 2; depth++)
                level.setBlock(new BlockPos(x + side * 5, y + rise, z - 5 + depth),
                        (rise == 0 || rise == 4 ? Asterion.SHADED_SHALE_BRICKS : Asterion.ANCIENT_BRICKS).defaultBlockState(), 2);
            level.setBlock(new BlockPos(x + side * 4, y + 5, z - 5), Asterion.ANCIENT_BRICK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, side < 0 ? Direction.EAST : Direction.WEST), 2);
            for (int dz = -2; dz <= 3; dz++) {
                int height = dz < 0 ? 3 : dz == 0 ? 2 : 1;
                for (int rise = 0; rise < height; rise++)
                    level.setBlock(new BlockPos(x + side * 6, y + rise, z + dz), Asterion.ANCIENT_BRICKS.defaultBlockState(), 2);
            }
        }
        for (int side : new int[]{-1, 1}) {
            // Broad bases and corbels give the arch weight without four isolated pillars.
            for (int dz = -6; dz <= -4; dz++)
                level.setBlock(new BlockPos(x + side * 5, y, z + dz), Asterion.SHADED_SHALE_BRICKS.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x + side * 5, y + 1, z - 6), Asterion.ANCIENT_STONE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH), 2);
            for (int dx = 4; dx <= 6; dx++)
                level.setBlock(new BlockPos(x + side * dx, y + 4, z - 5), Asterion.SHADED_SHALE_BRICK_SLAB.defaultBlockState(), 2);
            for (int dz = -2; dz <= 3; dz++) {
                int top = dz < 0 ? 3 : dz == 0 ? 2 : 1;
                level.setBlock(new BlockPos(x + side * 6, y + top, z + dz), Asterion.ANCIENT_STONE_SLAB.defaultBlockState(), 2);
            }
        }
        for (int dx = -3; dx <= 3; dx++)
            level.setBlock(new BlockPos(x + dx, y + 6, z - 5), Asterion.SHADED_SHALE_BRICKS.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x, y + 7, z - 5), Asterion.ANCIENT_BRICK_SLAB.defaultBlockState(), 2);
    }
}
