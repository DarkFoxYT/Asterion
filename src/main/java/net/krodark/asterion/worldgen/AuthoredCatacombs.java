package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.util.*;

/** Places the author's full-size modules on a deterministic, connected grid. */
public final class AuthoredCatacombs {
    public static final int BASE_Y = 19, SIZE = 19, CONNECTOR_Y = BASE_Y + 5;
    public static final int ARENA_BASE_Y = 1, ARENA_FLOOR_Y = 6, ARENA_RADIUS = 61;
    public static final List<String> TEMPLATES = List.of("corridor_cross_01", "corridor_cross_02",
            "corridor_deadend_01", "corridor_deadend_02", "corridor_straight_01", "corridor_straight_02",
            "corridor_straight_03", "corridor_straight_04", "corridor_t_01", "corridor_t_02",
            "crossing_01", "crossing_02", "ossuary_01", "parkour", "puzzleroom");
    private AuthoredCatacombs() { }
    public static boolean enabled() { return true; }
    public record Module(String name, Rotation rotation, int exits, int blocked) { }
    private static int bit(Direction side) { return switch(side) {
        case NORTH -> 1; case EAST -> 2; case SOUTH -> 4; case WEST -> 8; default -> 0;
    }; }
    public static int exits(long seed, int tx, int tz) {
        int mask = 0;
        for (Direction side : Direction.Plane.HORIZONTAL)
            if (CatacombLayout.connected(seed, tx, tz, side)) mask |= bit(side);
        // The root also accepts the arena approach from the west.
        if (tx == CatacombLayout.ROOT_X && tz == CatacombLayout.ROOT_Z) mask |= 8;
        return mask;
    }
    public static Module module(long seed, int tx, int tz) {
        int exits = exits(seed, tx, tz), degree = Integer.bitCount(exits);
        long hash = seed ^ tx * 0x632BE59BD9B4E019L ^ tz * 0x9E3779B97F4A7C15L;
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        String name;
        int nativeMask;
        if (degree == 1) {
            name = Math.floorMod(hash, 32) == 0 ? "puzzleroom" : ((hash >>> 8) & 1) == 0 ? "corridor_deadend_02" : "corridor_deadend_01";
            nativeMask = 1;
        } else if (degree == 2 && (exits == 5 || exits == 10)) {
            String[] choices = {"corridor_straight_01", "corridor_straight_02", "corridor_straight_03", "corridor_straight_04", "ossuary_01", "parkour"};
            int roll = Math.floorMod(hash, 64);
            name = roll < 4 ? "ossuary_01" : roll < 6 ? "parkour" : choices[(int)((hash >>> 8) & 3)]; nativeMask = 5;
        } else if (degree <= 3) {
            name = (hash & 1) == 0 ? "corridor_t_01" : "corridor_t_02";
            nativeMask = (hash & 1) == 0 ? 13 : 7;
        } else {
            name = (hash & 1) == 0 ? "corridor_cross_01" : "corridor_cross_02"; nativeMask = 15;
        }
        // Crossings are the authored surface-entry modules, not generic puzzle rooms.
        if ((tx == CatacombLayout.ROOT_X && tz == CatacombLayout.ROOT_Z)
                || degree >= 3 && Math.floorMod(hash, 24) == 0) {
            name = (hash & 1) == 0 ? "crossing_01" : "crossing_02"; nativeMask = 15;
        }
        Rotation[] rotations = Rotation.values();
        for (int i=0;i<rotations.length;i++) {
            Rotation rotation=rotations[(i+(int)((hash>>>16)&3))&3];
            int rotated = 0;
            for (Direction side : Direction.Plane.HORIZONTAL)
                if ((nativeMask & bit(side)) != 0) rotated |= bit(rotation.rotate(side));
            if ((rotated & exits) == exits) return new Module(name, rotation, exits, rotated & ~exits);
        }
        throw new IllegalStateException("No module fits exits " + exits);
    }
    public static void place(WorldGenLevel world, ChunkPos chunk) {
        ServerLevel level = world.getLevel();
        long seed = MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState());
        BoundingBox clip = new BoundingBox(chunk.getMinBlockX(), BASE_Y, chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), BASE_Y + 30, chunk.getMaxBlockZ());
        for (int tx = Math.floorDiv(chunk.getMinBlockX(), SIZE); tx <= Math.floorDiv(chunk.getMaxBlockX(), SIZE); tx++)
            for (int tz = Math.floorDiv(chunk.getMinBlockZ(), SIZE); tz <= Math.floorDiv(chunk.getMaxBlockZ(), SIZE); tz++) {
                if (!CatacombLayout.occupied(seed, tx, tz)) continue;
                Module module = module(seed, tx, tz);
                BlockPos origin = new BlockPos(tx * SIZE, BASE_Y, tz * SIZE);
                var template = level.getStructureManager().get(Asterion.id("catacombs/" + module.name()))
                        .orElseThrow(() -> new IllegalStateException("Missing authored crypt: " + module.name()));
                if (!template.getSize().equals(new net.minecraft.core.Vec3i(19, 31, 19)))
                    throw new IllegalStateException("Unexpected crypt size: " + module.name());
                // The last two layers of ordinary modules are an exterior roof cap/air.
                // Keep the existing maze floor and walls there; only crossings break the surface.
                BoundingBox roomClip = module.name().startsWith("crossing_") ? clip
                        : new BoundingBox(clip.minX(), clip.minY(), clip.minZ(), clip.maxX(), 47, clip.maxZ());
                template.placeInWorld(world, origin, origin, settings(roomClip).setRotation(module.rotation())
                                .setRotationPivot(new BlockPos(9, 0, 9)), RandomSource.create(seed ^ origin.asLong()), 18);
                if (module.name().startsWith("crossing_")) surfaceApproach(world, chunk, origin, seed);
                // No corner asset was supplied: rotate a T and close only its unused connector.
                for (Direction side : Direction.Plane.HORIZONTAL) if ((module.blocked() & bit(side)) != 0) {
                    BlockPos door = origin.offset(9, 5, 9).relative(side, 9);
                    for (int across = -3; across <= 3; across++) for (int y = -1; y <= 6; y++) {
                        BlockPos pos = door.relative(side.getClockWise(), across).above(y);
                        if (clip.isInside(pos)) world.setBlock(pos, Asterion.ANCIENT_BRICKS.defaultBlockState(), 2);
                    }
                }
            }
    }
    public static StructurePlaceSettings settings(BoundingBox clip) {
        return new StructurePlaceSettings().setBoundingBox(clip).setIgnoreEntities(true)
                // Preserve saved circuitry and shape across chunk boundaries; do not flood
                // dry components with the destination's old fluid or notify every brick.
                .setKnownShape(true).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK).addProcessor(JigsawReplacementProcessor.INSTANCE);
    }
    private static void surfaceApproach(WorldGenLevel world, ChunkPos chunk, BlockPos origin, long seed) {
        // Keep the authored hatch, winch and lever. Grade only the surface around them.
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            int wx = origin.getX() + x, wz = origin.getZ() + z;
            if (wx < chunk.getMinBlockX() || wx > chunk.getMaxBlockX() || wz < chunk.getMinBlockZ() || wz > chunk.getMaxBlockZ()) continue;
            int radius = Math.max(Math.abs(x-9), Math.abs(z-9));
            int surface = net.krodark.asterion.WorldGenerator.mazeFloorHeight(seed, wx, wz);
            int deck = Math.min(surface, 48 + Math.max(0, radius - 3));
            if (radius >= 3) {
                for (int y = 49; y <= deck; y++) world.setBlock(new BlockPos(wx, y, wz), Asterion.ANCIENT_BRICKS.defaultBlockState(), 2);
            }
            for (int y = Math.max(50, deck+1); y <= surface+4; y++) world.setBlock(new BlockPos(wx, y, wz), Blocks.AIR.defaultBlockState(), 2);
        }
    }
    public static void placeArena(ServerLevel level) {
        // Row order and +Z orientation are confirmed by part 8's south-facing exit.
        for (int part = 1; part <= 9; part++) {
            var template = level.getStructureManager().get(Asterion.id("catacombs/arena_part" + part)).orElseThrow();
            if (!template.getSize().equals(new net.minecraft.core.Vec3i(41, 48, 41)))
                throw new IllegalStateException("Arena part " + part + " must be 41x48x41");
            BlockPos origin = new BlockPos(-61 + ((part - 1) % 3) * 41, ARENA_BASE_Y,
                    -61 + ((part - 1) / 3) * 41);
            BoundingBox bounds = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX()+40, origin.getY()+47, origin.getZ()+40);
            template.placeInWorld(level, origin, origin, settings(bounds), RandomSource.create(part), 18);
        }
        // Connect the author's one exit to the root, entirely outside the arena footprint.
        for (int z = 62; z <= CatacombLayout.ROOT_CENTER; z++) corridor(level, 0, z);
        for (int x = 0; x <= CatacombLayout.ROOT_CENTER - 10; x++) corridor(level, x, CatacombLayout.ROOT_CENTER);
        // Preserve the terrain-ready markers, or chunk reload would regenerate over the arena.
        for (int cx = -4; cx <= 3; cx++) for (int cz = -4; cz <= 3; cz++) {
            var chunk = level.getChunk(cx, cz);
            level.setBlock(new BlockPos(cx*16, 1, cz*16), Blocks.BEDROCK.defaultBlockState(), 2);
            MazeNbtStructures.markCopperClean(chunk);
        }
    }
    private static void corridor(ServerLevel level, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (Math.abs(x + dx) <= ARENA_RADIUS && Math.abs(z + dz) <= ARENA_RADIUS) continue;
            level.setBlock(new BlockPos(x+dx, CONNECTOR_Y-1, z+dz), Asterion.ANCIENT_BRICKS.defaultBlockState(), 2);
            for (int y = 0; y < 4; y++) level.setBlock(new BlockPos(x+dx, CONNECTOR_Y+y, z+dz), Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
