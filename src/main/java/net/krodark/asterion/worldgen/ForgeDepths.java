package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.List;

/** Foundation shell for the authored Forge-biome NBT network beneath the catacombs. */
public final class ForgeDepths {
    public static final int FLOOR_Y = LabyrinthLevels.FORGE_FLOOR_Y;
    public static final int ROOF_Y = LabyrinthLevels.FORGE_ROOF_Y;
    private ForgeDepths() { }

    public static void generate(ChunkAccess chunk, long seed) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX(), maxX = chunk.getPos().getMaxBlockX();
        int minZ = chunk.getPos().getMinBlockZ(), maxZ = chunk.getPos().getMaxBlockZ();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            for (int y = FLOOR_Y - 2; y <= FLOOR_Y; y++)
                chunk.setBlockState(cursor.set(x, y, z), floor(seed, x, y, z), 0);
            for (int y = ROOF_Y - 2; y <= ROOF_Y; y++)
                chunk.setBlockState(cursor.set(x, y, z), Asterion.MAZESTEEL_BRICKS.defaultBlockState(), 0);
        }
    }

    private static BlockState floor(long seed, int x, int y, int z) {
        return (CatacombLayout.hash(seed + y * 31L, x, z) & 7L) == 0
                ? Asterion.MAZESTEEL_BRICKS.defaultBlockState()
                : Asterion.POLISHED_MAZESTEEL.defaultBlockState();
    }

    /** Enclosed spiral stair and landings link the root catacomb to the Forge district. */
    public static void carveAccess(ServerLevelAccessor level, ChunkPos chunk) {
        int center = CatacombLayout.ROOT_CENTER;
        int shaftX = center + 30, shaftZ = center;
        int bottom = FLOOR_Y + 1, top = AuthoredCatacombs.CONNECTOR_Y;
        BlockState masonry = Asterion.MAZESTEEL_BRICKS.defaultBlockState();

        // A solid outer shell and central pier prevent falls into the biome void.
        for (int x = shaftX - 4; x <= shaftX + 4; x++)
            for (int z = shaftZ - 4; z <= shaftZ + 4; z++) {
                if (!inside(chunk, x, z)) continue;
                for (int y = FLOOR_Y; y <= top + 4; y++) {
                    boolean floorOrRoof = y == FLOOR_Y || y == top + 4;
                    boolean outer = Math.abs(x - shaftX) == 4 || Math.abs(z - shaftZ) == 4;
                    boolean pier = Math.abs(x - shaftX) <= 1 && Math.abs(z - shaftZ) <= 1;
                    level.setBlock(new BlockPos(x, y, z), floorOrRoof || outer || pier
                            ? masonry : Blocks.AIR.defaultBlockState(), 18);
                }
            }

        List<BlockPos> ring = spiralRing();
        int horizontalSteps = (top - bottom) * 2 + 1;
        for (int step = 0; step < horizontalSteps; step++) {
            BlockPos local = ring.get(step % ring.size());
            BlockPos next = ring.get((step + 1) % ring.size());
            int x = shaftX + local.getX(), z = shaftZ + local.getZ();
            if (!inside(chunk, x, z)) continue;
            Direction facing = direction(next.getX() - local.getX(), next.getZ() - local.getZ());
            BlockPos stair = new BlockPos(x, bottom + step / 2, z);
            level.setBlock(stair, Asterion.POLISHED_MAZESTEEL_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, facing), 18);
            level.setBlock(stair.above(), Blocks.AIR.defaultBlockState(), 18);
            level.setBlock(stair.above(2), Blocks.AIR.defaultBlockState(), 18);
        }

        // The lower landing enters the authored Forge; the upper one opens directly
        // into the guaranteed root catacomb, so generation order cannot strand it.
        carveHall(level, chunk, center + 17, shaftX - 4, shaftZ, bottom, masonry);
        carveHall(level, chunk, center, shaftX - 4, shaftZ, top, masonry);
    }

    private static void carveHall(ServerLevelAccessor level, ChunkPos chunk, int minX, int maxX,
                                  int centerZ, int feetY, BlockState masonry) {
        for (int x = Math.min(minX, maxX); x <= Math.max(minX, maxX); x++)
            for (int dz = -3; dz <= 3; dz++) {
                int z = centerZ + dz;
                if (!inside(chunk, x, z)) continue;
                for (int y = feetY - 1; y <= feetY + 4; y++) {
                    boolean shell = Math.abs(dz) == 3 || y == feetY - 1 || y == feetY + 4;
                    level.setBlock(new BlockPos(x, y, z), shell ? masonry : Blocks.AIR.defaultBlockState(), 18);
                }
            }
    }

    private static List<BlockPos> spiralRing() {
        List<BlockPos> ring = new ArrayList<>(24);
        for (int x = -3; x <= 3; x++) ring.add(new BlockPos(x, 0, -3));
        for (int z = -2; z <= 3; z++) ring.add(new BlockPos(3, 0, z));
        for (int x = 2; x >= -3; x--) ring.add(new BlockPos(x, 0, 3));
        for (int z = 2; z >= -2; z--) ring.add(new BlockPos(-3, 0, z));
        return ring;
    }

    private static Direction direction(int dx, int dz) {
        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static boolean inside(ChunkPos chunk, int x, int z) {
        return x >= chunk.getMinBlockX() && x <= chunk.getMaxBlockX()
                && z >= chunk.getMinBlockZ() && z <= chunk.getMaxBlockZ();
    }
}
