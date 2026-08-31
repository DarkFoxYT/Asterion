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

/** A dry sanctuary under a raised grate, connected by ladder to the sewer galleries. */
public final class CatacombEntrances {
    private CatacombEntrances() { }
    public static boolean selected(ChunkPos chunk) { return Math.floorMod(chunk.x(), 8) == 4 && Math.floorMod(chunk.z(), 8) == 4; }
    public static void place(WorldGenLevel world, ChunkPos chunk, long seed) {
        if (!selected(chunk)) return;
        int x = chunk.getMinBlockX() + 8, z = chunk.getMinBlockZ() + 8;
        if (Math.abs(x) < 96 && Math.abs(z) < 96) return;
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++)
            if (MazeNbtStructures.generationLayout(seed).reserved(x + dx, z + dz)) return;
        int surface = WorldGenerator.mazeFloorHeight(seed, x, z), floor = surface - 7;
        var stone = Asterion.ANCIENT_BRICKS.defaultBlockState();
        var grate = Asterion.MAZESTEEL_BARS.defaultBlockState()
                .setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true)
                .setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            boolean edge = Math.abs(dx) == 6 || Math.abs(dz) == 6;
            for (int y = floor; y <= surface + 18; y++) {
                var state = y == floor || edge && y <= surface + 1 ? stone
                        : y == surface + 1 ? grate : Blocks.AIR.defaultBlockState();
                world.setBlock(new BlockPos(x + dx, y, z + dz), state, 2);
            }
        }
        // Four walk-up lips connect the raised grate to the surrounding maze corridors.
        for (Direction side : Direction.Plane.HORIZONTAL) for (int r = 6; r <= 7; r++) {
            BlockPos step = new BlockPos(x, surface, z).relative(side, r);
            world.setBlock(step, stone, 2);
            for (int y = 1; y <= 4; y++) world.setBlock(step.above(y), Blocks.AIR.defaultBlockState(), 2);
        }
        BlockPos ladder = new BlockPos(x + 4, 0, z);
        for (int y = CatacombLayout.WATER_Y + 1; y <= surface + 1; y++) {
            world.setBlock(new BlockPos(ladder.getX() + 1, y, z), stone, 2);
            world.setBlock(new BlockPos(ladder.getX(), y, z), Blocks.LADDER.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 2);
        }
        world.setBlock(new BlockPos(ladder.getX(), surface + 2, z), Blocks.DARK_OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 2);
        // Bottom exit joins an existing tile gallery; the route stays inside this chunk.
        int galleryZ = chunk.getMinBlockZ() + 1;
        for (int pz = galleryZ; pz < z; pz++) for (int y = 8; y <= 10; y++)
            world.setBlock(new BlockPos(ladder.getX(), y, pz), Blocks.AIR.defaultBlockState(), 2);
        world.setBlock(new BlockPos(x, floor, z), Blocks.LODESTONE.defaultBlockState(), 2);
        for (int dx : new int[]{-4, 4}) for (int dz : new int[]{-4, 4}) {
            world.setBlock(new BlockPos(x + dx, floor + 1, z + dz), stone, 2);
            world.setBlock(new BlockPos(x + dx, floor + 2, z + dz), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
        }
        BlockPos supply = new BlockPos(x - 3, floor + 1, z);
        world.setBlock(supply, Blocks.BARREL.defaultBlockState(), 2);
        if (world.getBlockEntity(supply) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel) {
            barrel.setItem(0, new net.minecraft.world.item.ItemStack(Asterion.CATACOMB_GRAPPLING_HOOK));
            barrel.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
            barrel.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 6));
            barrel.setChanged();
        }
    }
    public static BlockPos checkpoint(ServerLevel level, BlockPos position) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) return null;
        ChunkPos chunk = ChunkPos.containing(position);
        if (!selected(chunk) || !level.getChunkSource().hasChunk(chunk.x(), chunk.z())) return null;
        int x = chunk.getMinBlockX() + 8, z = chunk.getMinBlockZ() + 8;
        if (Math.abs(position.getX() - x) > 6 || Math.abs(position.getZ() - z) > 6) return null;
        for (int y = 41; y <= 48; y++) {
            BlockPos marker = new BlockPos(x, y, z);
            if (level.getBlockState(marker).is(Blocks.LODESTONE) && position.getY() >= y && position.getY() <= y + 12
                    && level.getBlockState(marker.above()).getCollisionShape(level, marker.above()).isEmpty()
                    && level.getBlockState(marker.above(2)).getCollisionShape(level, marker.above(2)).isEmpty()) return marker.above();
        }
        return null;
    }
}
