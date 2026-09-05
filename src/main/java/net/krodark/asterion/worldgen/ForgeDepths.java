package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
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

    public static boolean isStairModule(int tx, int tz) {
        int x = tx * 19 + 9, z = tz * 19 + 9;
        return x == AuthoredForge.districtCenter(x) - 19 && z == AuthoredForge.districtCenter(z);
    }

    /** Align the template's bottom jigsaw directly with the authored Forge's west jigsaw. */
    public static void carveAccess(ServerLevelAccessor world, ChunkPos chunk) {
        var level = world instanceof net.minecraft.server.level.ServerLevel server ? server
                : ((net.minecraft.world.level.WorldGenLevel)world).getLevel();
        BlockPos center = AuthoredForge.entranceCenter(level, chunk);
        int cx = center.getX(), cz = center.getZ(), feet = center.getY();
        if (chunk.getMaxBlockX() < cx - 50 || chunk.getMinBlockX() > cx - 9
                || chunk.getMaxBlockZ() < cz - 11 || chunk.getMinBlockZ() > cz + 11) return;
        var template = level.getStructureManager().get(Asterion.id("forge/staircase")).orElseThrow();
        var bottomPort = template.getJigsaws(BlockPos.ZERO, net.minecraft.world.level.block.Rotation.NONE).stream()
                .filter(port -> port.info().pos().getY() == 1).findFirst().orElseThrow();
        BlockPos socket = AuthoredForge.westSocket(level, chunk);
        BlockPos origin = socket.west().subtract(bottomPort.info().pos());
        var clip = new net.minecraft.world.level.levelgen.structure.BoundingBox(chunk.getMinBlockX(), level.getMinY(),
                chunk.getMinBlockZ(), chunk.getMaxBlockX(), LabyrinthLevels.MAZE_FLOOR_Y - 2, chunk.getMaxBlockZ());
        template.placeInWorld(world, origin, origin, AuthoredCatacombs.settings(clip),
                net.minecraft.util.RandomSource.create(origin.asLong()), 18);
        long seed = MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState());
        int tx = Math.floorDiv(origin.getX(), 19), tz = Math.floorDiv(origin.getZ(), 19);
        int exits = AuthoredCatacombs.exits(seed, tx, tz);
        for (var port : template.getJigsaws(origin, net.minecraft.world.level.block.Rotation.NONE)) {
            if (port.info().pos().getY() != AuthoredCatacombs.CONNECTOR_Y) continue;
            Direction face = net.minecraft.world.level.block.JigsawBlock.getFrontFacing(port.info().state());
            int bit = switch (face) { case NORTH -> 1; case EAST -> 2; case SOUTH -> 4; case WEST -> 8; default -> 0; };
            if ((exits & bit) != 0) {
                // Open both saved socket faces, including the neighbour in older chunks.
                for (int depth = 0; depth <= 1; depth++) for (int side = -2; side <= 2; side++)
                    for (int y = 0; y <= 5; y++) {
                        BlockPos pos = port.info().pos().relative(face, depth).relative(face.getClockWise(), side).above(y);
                        if (clip.isInside(pos)) world.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                    }
                continue;
            }
            for (int side = -3; side <= 3; side++) for (int y = -1; y <= 6; y++) {
                BlockPos pos = port.info().pos().relative(face.getClockWise(), side).above(y);
                if (clip.isInside(pos)) world.setBlock(pos, Asterion.ANCIENT_BRICKS.defaultBlockState(), 18);
            }
        }
        ForgeCaveEntrance.place(world, chunk, seed, cx, cz, feet);
        world.setBlock(accessMarker(chunk), accessRevision(), 18);
    }

    private static BlockPos accessMarker(ChunkPos chunk) { return new BlockPos(chunk.getMinBlockX(), 15, chunk.getMinBlockZ()); }
    private static BlockState accessRevision() { return Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 5); }
}
