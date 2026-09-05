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

    /** The saved west socket is thirteen blocks above the template's base. */
    public static void carveAccess(ServerLevelAccessor world, ChunkPos chunk) {
        var level = world instanceof net.minecraft.server.level.ServerLevel server ? server
                : ((net.minecraft.world.level.WorldGenLevel)world).getLevel();
        BlockPos center = AuthoredForge.entranceCenter(level, chunk);
        if (center == null) return;
        int cx = center.getX(), cz = center.getZ(), feet = center.getY();
        var masonry = Asterion.MAZESTEEL_BRICKS.defaultBlockState();
        var plan = new java.util.HashMap<BlockPos, Cell>();
        // Boundary halls continue into the neighbouring district on both axes.
        int half = AuthoredForge.DISTRICT_SPACING / 2;
        for (int edge : new int[]{-half, half}) {
            hall(plan, chunk, chunk.getMinBlockX() - 2, chunk.getMaxBlockX() + 2, cz + edge, feet, masonry);
            for (int z = chunk.getMinBlockZ() - 2; z <= chunk.getMaxBlockZ() + 2; z++)
                tunnel(plan, chunk, cx + edge, z, feet, masonry, null, true);
        }
        hall(plan, chunk, cx - half, cx - 20, cz, feet, masonry);
        // Separate the rising staircase from the mine landing so their headroom never overlaps.
        hall(plan, chunk, cx - 20, cx - 18, cz, feet, masonry);
        for (int z = cz - 8; z <= cz; z++) tunnel(plan, chunk, cx - 20, z, feet, masonry, null, true);
        int rise = AuthoredCatacombs.CONNECTOR_Y - feet;
        for (int step = 0; step <= rise; step++)
            tunnel(plan, chunk, cx - 20 - step, cz - 8, feet + step, masonry,
                    Asterion.POLISHED_MAZESTEEL_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST), false);
        int topX = cx - 20 - rise;
        for (int z = cz - 8; z <= cz; z++) tunnel(plan, chunk, topX, z, AuthoredCatacombs.CONNECTOR_Y, masonry, null, true);
        hall(plan, chunk, topX, cx - 57, cz, AuthoredCatacombs.CONNECTOR_Y, masonry);

        int shaftX = Math.floorDiv(cx - 24, 64) * 64;
        long seed = MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState());
        int bottom = ShaleCaves.floorY(seed, shaftX, cz) + 1;
        for (int x = shaftX - 2; x <= shaftX + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) {
            if (!inside(chunk, x, z)) continue;
            for (int y = bottom - 1; y <= feet + 4; y++) {
                boolean shell = Math.abs(x - shaftX) == 2 || Math.abs(z - cz) == 2 || y == bottom - 1 || y == feet + 4;
                put(plan, new BlockPos(x, y, z), shell ? masonry : Blocks.AIR.defaultBlockState(), y == bottom - 1 ? 3 : shell ? 1 : 2);
            }
        }
        for (int y = bottom; y <= feet; y++) if (inside(chunk, shaftX - 1, cz))
            put(plan, new BlockPos(shaftX - 1, y, cz), Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST), 5);
        hall(plan, chunk, shaftX, cx - 20, cz, feet, masonry);
        // Open the lowest landing into the cave instead of leaving a sealed ladder well.
        for (int x = shaftX; x <= shaftX + 4; x++) for (int z = cz - 1; z <= cz + 1; z++)
            if (inside(chunk, x, z)) for (int y = bottom; y <= bottom + 3; y++)
                put(plan, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 4);
        // Restore the ladder through the upper landing's floor opening.
        if (inside(chunk, shaftX - 1, cz)) for (int y = bottom; y <= feet; y++)
            put(plan, new BlockPos(shaftX - 1, y, cz), Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST), 5);
        for (var entry : plan.entrySet()) world.setBlock(entry.getKey(), entry.getValue().state(), 18);
        world.setBlock(accessMarker(chunk), accessRevision(), 18);
    }

    public static void repairAccess(net.minecraft.server.level.ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) {
        if (!chunk.getBlockState(accessMarker(chunk.getPos())).equals(accessRevision())) carveAccess(level, chunk.getPos());
    }

    private static BlockPos accessMarker(ChunkPos chunk) { return new BlockPos(chunk.getMinBlockX(), 15, chunk.getMinBlockZ()); }
    private static BlockState accessRevision() { return Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 3); }

    private static void hall(java.util.Map<BlockPos, Cell> plan, ChunkPos chunk, int minX, int maxX, int z, int feet, BlockState masonry) {
        for (int x = Math.min(minX, maxX); x <= Math.max(minX, maxX); x++) tunnel(plan, chunk, x, z, feet, masonry, null, false);
    }

    private static void tunnel(java.util.Map<BlockPos, Cell> plan, ChunkPos chunk, int x, int z, int feet,
                               BlockState masonry, BlockState step, boolean alongZ) {
        for (int side = -2; side <= 2; side++) {
            int px = x + (alongZ ? side : 0), pz = z + (alongZ ? 0 : side);
            if (!inside(chunk, px, pz)) continue;
            for (int dy = -1; dy <= 4; dy++) {
                BlockState state = Math.abs(side) == 2 || dy == 4 || dy == -1 ? masonry : Blocks.AIR.defaultBlockState();
                int distance = alongZ ? z : x;
                if (dy == -1 && Math.abs(side) < 2) state = step != null ? step
                        : Asterion.POLISHED_MAZESTEEL.defaultBlockState();
                if (step == null && Math.floorMod(distance, 12) == 0) {
                    if (Math.abs(side) == 2) state = Asterion.MAZESTEEL_BLOCK.defaultBlockState();
                    if (side == 0 && dy == 3) state = Blocks.LANTERN.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
                }
                put(plan, new BlockPos(px, feet + dy, pz), state, dy == -1 ? 3 : state.isAir() ? 2 : 1);
            }
        }
    }

    private record Cell(BlockState state, int priority) {}
    private static void put(java.util.Map<BlockPos, Cell> plan, BlockPos pos, BlockState state, int priority) {
        Cell next = new Cell(state, priority);
        plan.merge(pos, next, (old, value) -> value.priority() >= old.priority() ? value : old);
    }

    private static boolean inside(ChunkPos chunk, int x, int z) {
        return x >= chunk.getMinBlockX() && x <= chunk.getMaxBlockX()
                && z >= chunk.getMinBlockZ() && z <= chunk.getMaxBlockZ();
    }
}
