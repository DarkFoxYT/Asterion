package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;



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
            for (int y = FLOOR_Y + 1; y < ROOF_Y - 2; y++)
                chunk.setBlockState(cursor.set(x, y, z),
                        (ShaleCaves.shaded(seed, x, y, z) ? Asterion.SHADED_SHALE : Asterion.SHALE).defaultBlockState(), 0);
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


    public static void carveAccess(ServerLevelAccessor world, ChunkPos chunk) {
        var level = world instanceof net.minecraft.server.level.ServerLevel server ? server
                : ((net.minecraft.world.level.WorldGenLevel)world).getLevel();
        BlockPos center = AuthoredForge.entranceCenter(level, chunk);
        int cx = center.getX(), cz = center.getZ(), feet = center.getY();
        if (chunk.getMaxBlockX() < cx - 64 || chunk.getMinBlockX() > cx + 64
                || chunk.getMaxBlockZ() < cz - 64 || chunk.getMinBlockZ() > cz + 64) return;
        var template = level.getStructureManager().get(Asterion.id("forge/staircase")).orElseThrow();
        var entrance = AuthoredForge.entrance(level, chunk);
        BlockPos socket = entrance.position();
        var unrotatedPort = template.getJigsaws(BlockPos.ZERO, net.minecraft.world.level.block.Rotation.NONE).stream()
                .min(java.util.Comparator.comparingInt(p -> p.info().pos().getY())).orElseThrow();
        Direction localFace = net.minecraft.world.level.block.JigsawBlock.getFrontFacing(unrotatedPort.info().state());
        var rotation = net.minecraft.world.level.block.Rotation.NONE;
        for (var candidate : net.minecraft.world.level.block.Rotation.values())
            if (candidate.rotate(localFace) == entrance.facing().getOpposite()) { rotation = candidate; break; }
        BlockPos rotatedPort = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
                unrotatedPort.info().pos(), net.minecraft.world.level.block.Mirror.NONE, rotation, BlockPos.ZERO);
        BlockPos origin = socket.relative(entrance.facing()).subtract(rotatedPort);
        var bounds = template.getBoundingBox(new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(rotation), origin);


        var clip = new net.minecraft.world.level.levelgen.structure.BoundingBox(chunk.getMinBlockX(), level.getMinY(),
                chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY() - 1, chunk.getMaxBlockZ());
        if (bounds.intersects(clip)) {
            // Clip at chunk borders, not the maze floor: the authored landing includes its own roof.
            var stairClip = new net.minecraft.world.level.levelgen.structure.BoundingBox(
                    Math.max(clip.minX(), bounds.minX()), Math.max(clip.minY(), bounds.minY()), Math.max(clip.minZ(), bounds.minZ()),
                    Math.min(clip.maxX(), bounds.maxX()), Math.min(clip.maxY(), bounds.maxY()),
                    Math.min(clip.maxZ(), bounds.maxZ()));
            template.placeInWorld(world, origin, origin, AuthoredCatacombs.settings(stairClip).setRotation(rotation),
                    net.minecraft.util.RandomSource.create(origin.asLong()), 18);
        }
        long seed = MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState());
        int tx = Math.floorDiv(bounds.minX(), 19), tz = Math.floorDiv(bounds.minZ(), 19);
        int exits = AuthoredCatacombs.exits(seed, tx, tz);
        for (var port : template.getJigsaws(origin, rotation)) {
            if (port.info().pos().getY() != AuthoredCatacombs.CONNECTOR_Y) continue;
            Direction face = net.minecraft.world.level.block.JigsawBlock.getFrontFacing(port.info().state());
            int bit = switch (face) { case NORTH -> 1; case EAST -> 2; case SOUTH -> 4; case WEST -> 8; default -> 0; };
            if ((exits & bit) != 0) {

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

        if (bounds.intersects(clip))
            for (BlockPos pos : BlockPos.betweenClosed(Math.max(clip.minX(), bounds.minX()), Math.max(clip.minY(), bounds.minY()),
                    Math.max(clip.minZ(), bounds.minZ()), Math.min(clip.maxX(), bounds.maxX()),
                    Math.min(clip.maxY(), bounds.maxY()), Math.min(clip.maxZ(), bounds.maxZ()))) {
                var state = world.getBlockState(pos);
                if (state.getBlock() instanceof net.krodark.asterion.block.DirectionalGateBlock
                        && !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN))
                    world.setBlock(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN, true), 18);
            }
        buildInteriorStairs(world, clip, entrance);
        ForgeCaveEntrance.place(world, chunk, seed, cx, cz, feet);
        world.setBlock(accessMarker(chunk), accessRevision(), 18);
    }

    /** Three-wide walking access from the actual doorway down to the forge floor. */
    private static void buildInteriorStairs(ServerLevelAccessor world,
            net.minecraft.world.level.levelgen.structure.BoundingBox clip, AuthoredForge.Entrance entrance) {
        Direction inward = entrance.facing().getOpposite();
        Direction side = inward.getClockWise();
        int drop = Math.max(0, entrance.position().getY() - (FLOOR_Y + 1));
        for (int step = 0; step <= drop + 2; step++) {
            BlockPos feet = entrance.position().relative(inward, step).below(Math.min(step, drop));
            for (int width = -1; width <= 1; width++) {
                BlockPos cell = feet.relative(side, width);
                if (!clip.isInside(cell)) continue;
                BlockState tread = step < drop
                        ? Asterion.POLISHED_MAZESTEEL_STAIRS.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.StairBlock.FACING, entrance.facing())
                        : Asterion.POLISHED_MAZESTEEL.defaultBlockState();
                for (int depth = 1; depth <= 3; depth++)
                    if (clip.isInside(cell.below(depth))) world.setBlock(cell.below(depth),
                            depth == 1 ? tread : Asterion.POLISHED_MAZESTEEL.defaultBlockState(), 18);
                for (int head = 0; head < 4; head++)
                    if (clip.isInside(cell.above(head))) world.setBlock(cell.above(head), Blocks.AIR.defaultBlockState(), 18);
            }
        }
    }

    public static void repairAccess(net.minecraft.server.level.ServerLevel level,
                                    net.minecraft.world.level.chunk.LevelChunk chunk) {
        if (!chunk.getBlockState(accessMarker(chunk.getPos())).equals(accessRevision()))
            carveAccess(level, chunk.getPos());
    }

    private static BlockPos accessMarker(ChunkPos chunk) { return new BlockPos(chunk.getMinBlockX(), 15, chunk.getMinBlockZ()); }
    private static BlockState accessRevision() { return Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 7); }
}
