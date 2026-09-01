package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** A dry sanctuary under a flush grate, connected by ladder to the sewer galleries. */
public final class CatacombEntrances {
    private CatacombEntrances() { }
    public static boolean selected(ChunkPos chunk) { return Math.floorMod(chunk.x(), 8) == 4 && Math.floorMod(chunk.z(), 8) == 4; }
    public static void place(WorldGenLevel world, ChunkPos chunk, long seed) {
        // Every intersecting chunk writes its own section, regardless of generation order.
        for (int cx = chunk.x() - 2; cx <= chunk.x() + 2; cx++)
            for (int cz = chunk.z() - 2; cz <= chunk.z() + 2; cz++) {
                ChunkPos candidate = new ChunkPos(cx, cz);
                if (selected(candidate) && allowed(seed, candidate)) connect(world, chunk, candidate);
            }
        if (!selected(chunk)) return;
        int x = chunk.getMinBlockX() + 8, z = chunk.getMinBlockZ() + 8;
        if (!allowed(seed, chunk)) return;
        int surface = WorldGenerator.mazeFloorHeight(seed, x, z), floor = surface - 7;
        var stone = Asterion.ANCIENT_BRICKS.defaultBlockState();
        var grate = Asterion.MAZESTEEL_BARS.defaultBlockState()
                .setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true)
                .setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            boolean edge = Math.abs(dx) == 6 || Math.abs(dz) == 6;
            for (int y = floor; y <= surface + 18; y++) {
                boolean doorway = edge && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1)
                        && y > surface && y <= surface + 3;
                var state = y == floor || edge && y <= surface + 4 && !doorway ? stone
                        : y == surface ? grate : Blocks.AIR.defaultBlockState();
                world.setBlock(new BlockPos(x + dx, y, z + dz), state, 2);
            }
        }
        // Flush walking deck; only the entrance arches protrude above the local maze floor.
        for (Direction side : Direction.Plane.HORIZONTAL) for (int r = 6; r <= 7; r++) for (int width = -1; width <= 1; width++) {
            BlockPos step = new BlockPos(x, surface, z).relative(side, r).relative(side.getClockWise(), width);
            world.setBlock(step, stone, 2);
            for (int y = 1; y <= 3; y++) world.setBlock(step.above(y), Blocks.AIR.defaultBlockState(), 2);
        }
        BlockPos ladder = new BlockPos(x + 4, 0, z);
        for (int y = CatacombLayout.WATER_Y + 1; y <= surface; y++) {
            world.setBlock(new BlockPos(ladder.getX() + 1, y, z), stone, 2);
            world.setBlock(new BlockPos(ladder.getX(), y, z), Blocks.LADDER.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 2);
        }
        world.setBlock(new BlockPos(ladder.getX(), surface + 1, z), Blocks.DARK_OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 2);
        world.setBlock(new BlockPos(x, floor, z), Blocks.LODESTONE.defaultBlockState(), 2);
        for (int dx : new int[]{-4, 4}) for (int dz : new int[]{-4, 4}) {
            world.setBlock(new BlockPos(x + dx, floor + 1, z + dz), stone, 2);
            world.setBlock(new BlockPos(x + dx, floor + 2, z + dz), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
        }
        BlockPos supply = new BlockPos(x - 3, floor + 1, z);
        world.setBlock(supply, Blocks.BARREL.defaultBlockState(), 2);
        if (world.getBlockEntity(supply) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel) {
            barrel.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
            barrel.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 6));
            barrel.setChanged();
        }
    }

    public static boolean allowed(long seed, ChunkPos chunk) {
        int x = chunk.getMinBlockX() + 8, z = chunk.getMinBlockZ() + 8;
        if (Math.abs(x) < 96 && Math.abs(z) < 96) return false;
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++)
            if (MazeNbtStructures.generationLayout(seed).reserved(x + dx, z + dz)) return false;
        return true;
    }

    private static void connect(WorldGenLevel world, ChunkPos clip, ChunkPos entrance) {
        int ex = entrance.getMinBlockX() + 12, ez = entrance.getMinBlockZ() + 8;
        int hx = Math.floorDiv(ex, CatacombLayout.TILE) * CatacombLayout.TILE + CatacombLayout.TILE / 2;
        int hz = Math.floorDiv(ez, CatacombLayout.TILE) * CatacombLayout.TILE + CatacombLayout.TILE / 2;
        for (int x = clip.getMinBlockX(); x <= clip.getMaxBlockX(); x++)
            for (int z = clip.getMinBlockZ(); z <= clip.getMaxBlockZ(); z++) {
                boolean route = x >= Math.min(ex, hx) - 1 && x <= Math.max(ex, hx) + 1 && Math.abs(z - ez) <= 1
                        || Math.abs(x - hx) <= 1 && z >= Math.min(ez, hz) - 1 && z <= Math.max(ez, hz) + 1;
                if (!route) continue;
                world.setBlock(new BlockPos(x, CatacombLayout.WATER_Y, z), Asterion.ANCIENT_STONE.defaultBlockState(), 2);
                for (int y = CatacombLayout.WATER_Y + 1; y <= CatacombLayout.WATER_Y + 4; y++)
                    world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
    }
    public static BlockPos checkpoint(ServerLevel level, BlockPos position) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) return null;
        if (AuthoredCatacombs.enabled()) {
            int tx = Math.floorDiv(position.getX(), AuthoredCatacombs.SIZE);
            int tz = Math.floorDiv(position.getZ(), AuthoredCatacombs.SIZE);
            if (CatacombLayout.reserved(tx, tz)) return null;
            long seed = MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState());
            if (!CatacombLayout.occupied(seed, tx, tz) || !AuthoredCatacombs.module(seed, tx, tz).name().startsWith("crossing_")) return null;
            BlockPos landing = new BlockPos(tx * 19 + 7, AuthoredCatacombs.BASE_Y + 30, tz * 19 + 9);
            if (!level.getChunkSource().hasChunk(landing.getX() >> 4, landing.getZ() >> 4)) return null;
            return !level.getBlockState(landing.below()).getCollisionShape(level, landing.below()).isEmpty()
                    && level.getBlockState(landing).getCollisionShape(level, landing).isEmpty()
                    && level.getBlockState(landing.above()).getCollisionShape(level, landing.above()).isEmpty() ? landing : null;
        }
        ChunkPos chunk = ChunkPos.containing(position);
        if (!selected(chunk) || !level.getChunkSource().hasChunk(chunk.x(), chunk.z())) return null;
        int x = chunk.getMinBlockX() + 8, z = chunk.getMinBlockZ() + 8;
        if (Math.abs(position.getX() - x) > 6 || Math.abs(position.getZ() - z) > 6) return null;
        int expectedFloor = WorldGenerator.mazeFloorHeight(MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState()), x, z) - 7;
        BlockPos marker = new BlockPos(x, expectedFloor, z);
        if (level.getBlockState(marker).is(Blocks.LODESTONE) && position.getY() >= expectedFloor && position.getY() <= expectedFloor + 12
                && level.getBlockState(marker.above()).getCollisionShape(level, marker.above()).isEmpty()
                && level.getBlockState(marker.above(2)).getCollisionShape(level, marker.above(2)).isEmpty()) return marker.above();
        return null;
    }
}
