package net.krodark.asterion.update.underworld.world;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.krodark.asterion.block.ShaleFormationBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** A shared river tunnel opens onto the shore of an unbounded subterranean sea. */
public final class UnderworldTerrain {
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 159;
    public static final int WATER_Y = 47;
    public static final int START_Z = -176;
    public static final int END_Z = 1024;
    public static final int SPAWN_Z = -112;
    public static final int SPAWN_X = (int)Math.floor(riverCenter(SPAWN_Z) - 15);
    public static final int SPAWN_Y = WATER_Y + 3;
    public static final int FERRY_Z = 58;

    private UnderworldTerrain() { }

    public static double riverCenter(double z) {
        return Math.sin(z * .008) * 18.0
                + Math.sin(z * .019 + 1.7) * 9.0
                + Math.sin(z * .043 + .4) * 4.0;
    }

    /** Shared world-space water height, including the renderer's horizontal crest deformation. */
    public static double waveHeight(double x, double z, double ticks) {
        return UnderworldWaves.height(x, z, ticks);
    }
    public static void generate(ChunkAccess chunk, long seed) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                Column column = column(seed, x, z);
                // Material noise is evaluated once per column, not for every buried block.
                boolean shaded = octaves(seed ^ 0x5ADE, x * .019, z * .019) > .08;
                BlockState stone = (shaded ? Asterion.DEAD_STONE_2 : Asterion.DEAD_STONE).defaultBlockState();
                Details details = details(seed, x, z, column);
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    BlockState state;
                    if (y == MIN_Y || y == MAX_Y) state = Blocks.BEDROCK.defaultBlockState();
                    else if (!column.open || y <= column.floor || y >= column.roof)
                        state = stone;
                    else if (y <= WATER_Y)
                        state = Blocks.WATER.defaultBlockState();
                    else if (atmosphericLight(x, y, z, column))
                        state = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 9);
                    else state = Blocks.AIR.defaultBlockState();
                    if (column.open && y > MIN_Y && y < MAX_Y) {
                        if (y == column.floor && details.mud) state = Blocks.MUD.defaultBlockState();
                        if (y > column.floor && y < column.roof) {
                            if (y <= column.floor + details.rock) state = stone;
                            else if (y <= column.floor + details.rock + details.spike) {
                                int remaining = column.floor + details.rock + details.spike - y;
                                state = formation(false, remaining, details.spike, y <= WATER_Y);
                            } else if (y >= column.roof - details.hanging) {
                                state = formation(true, y - (column.roof - details.hanging), details.hanging, y <= WATER_Y);
                            }
                            if (details.waterfall && y > WATER_Y)
                                state = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
                            BlockState settlement = settlement(x, y, z, column);
                            if (settlement != null) state = settlement;
                        }
                    }
                    chunk.setBlockState(pos.set(x, y, z), state, 0);
                }
            }
        }
    }

    private static Column column(long seed, int x, int z) {
        double center = riverCenter(z);
        double offset = x - center;
        double lateral = Math.abs(offset);
        double mouth = smooth((z + 36.0) / 80.0);
        double width = tunnelWidth(seed, z) + mouth * 24;
        // The coast extends sideways; the sea never closes at the end of the ferry route.
        double coast = 30 + 9 * octaves(seed ^ 0xC0457, x * .009, 0);
        double seaDistance = z - coast;
        boolean seaCavern = seaDistance > -24;
        boolean tunnel = z >= START_Z && lateral < width;
        boolean open = seaCavern || tunnel;
        double channel = 8.5 + 1.5 * octaves(seed ^ 0x71AE, z * .018, 0);
        double bank = smooth((lateral - channel) / 8);
        double floor = WATER_Y - 9 + bank * 13
                + octaves(seed ^ 0xF100D, x * .045, z * .045) * 1.5;
        // A flatter vault with steep walls, and occasional much taller chambers.
        double arch = Math.pow(Math.max(0, 1 - Math.pow(lateral / width, 4)), .35);
        double tall = smooth((octaves(seed ^ 0x7411, z * .018, 0) + .15) / .55);
        double roof = WATER_Y + 8 + arch * (29 + tall * 43 + mouth * 36
                + 5 * octaves(seed ^ 0xC4A7E, x * .02, z * .02));
        if (seaCavern) {
            // Broad eroded beach, one continuous waterline, deep open sea beyond.
            double depth = smooth((seaDistance + 3) / 90.0);
            double seaFloor = WATER_Y + 4 - depth * 34
                    + octaves(seed ^ 0x5EA, x * .012, z * .012) * 2 * depth;
            floor = tunnel ? Math.min(floor, seaFloor) : seaFloor;
            double seaRoof = 133 + 12 * octaves(seed ^ 0xA2C4, x * .008, z * .008);
            double opening = smooth((seaDistance + 24) / 55.0);
            roof = tunnel ? roof + (seaRoof - roof) * opening
                    : seaFloor + 7 + (seaRoof - seaFloor - 7) * Math.sqrt(opening);
        }
        // The left bank is the player entrance, within the same vaulted waterway.
        boolean path = tunnel && offset >= -19 && offset <= -11 && z <= 35;
        double landingT = smooth((z - 12.0) / (FERRY_Z - 12.0));
        double landingX = center - 15 + landingT * 11.5;
        if (z >= 12 && z <= FERRY_Z + 4 && Math.abs(x - landingX) <= 1.5
                && x + 1 <= center - 1.25) {
            open = true;
            path = true;
        }
        if (tunnel && lateral < channel && z < 150) floor = Math.min(floor, WATER_Y - 7);
        // The player wakes in a dry cavern. The Styx is revealed around the bend instead of
        // inexplicably running through the entrance room.
        if (tunnel && z < 18 && lateral < channel + 3) floor = WATER_Y + 1;
        if (path) {
            floor = WATER_Y + 1;
            roof = Math.max(roof, floor + 12);
        }
        // Small isolated puddles on the cave floor, away from the walking route.
        if (tunnel && !path && z < -20 && lateral > 4 && lateral < 11
                && octaves(seed ^ 0xADD1E, x * .13, z * .13) > .48)
            floor = WATER_Y - 1;
        // Layered buttresses frame the mouth without narrowing the ferry channel.
        if (open && !path && z > -24 && z < 55 && lateral > 23) {
            double shelf = Math.max(0, octaves(seed ^ 0xB077, x * .07, z * .055) - .12);
            floor += Math.floor(shelf * 11) * 3;
            roof -= Math.floor(shelf * 5) * 2;
        }
        // Sparse, column-coherent teeth make the huge sea cave read as eroded limestone.
        // Keep the authored approach and ferry lane clear.
        if (open && !path && z > 80 && lateral > 13) {
            double cells = octaves(seed ^ 0x57A1AC71L, x * .085, z * .085);
            double detail = octaves(seed ^ 0x51A6L, x * .19, z * .19);
            if (cells > .55) roof -= (cells - .55) * 31 + Math.max(0, detail) * 5;
            if (cells < -.62 && floor > WATER_Y - 25)
                floor += (-cells - .62) * 18 + Math.max(0, -detail) * 4;
            if (roof - floor < 7) roof = floor + 7;
        }
        return new Column(open, (int)Math.floor(floor), (int)Math.ceil(roof), path);
    }

    private static double tunnelWidth(long seed, int z) {
        return 30 + 7 * octaves(seed, z * .016, 0);
    }

    private static BlockState formation(boolean hanging, int remaining, int length, boolean waterlogged) {
        return Asterion.SHALE_SPIKE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION,
                        hanging ? net.minecraft.core.Direction.DOWN : net.minecraft.core.Direction.UP)
                .setValue(net.minecraft.world.level.block.PointedDripstoneBlock.THICKNESS,
                        remaining == 0 ? net.minecraft.world.level.block.state.properties.DripstoneThickness.TIP
                        : remaining == 1 ? net.minecraft.world.level.block.state.properties.DripstoneThickness.FRUSTUM
                        : remaining == length - 1 ? net.minecraft.world.level.block.state.properties.DripstoneThickness.BASE
                        : net.minecraft.world.level.block.state.properties.DripstoneThickness.MIDDLE)
                .setValue(BlockStateProperties.WATERLOGGED, waterlogged);
    }

    /** All placement decisions use world coordinates, never chunk-local randomness. */
    private static Details details(long seed, int x, int z, Column column) {
        if (!column.open) return new Details(false, 0, 0, 0, false);
        double offset = x - riverCenter(z);
        double pathCenter = -15 + Math.sin(z * .067) * 1.35;
        double patch = octaves(seed ^ 0xD17, x * .15, z * .15);
        boolean mud = column.floor >= WATER_Y - 2 && column.floor <= WATER_Y + 2
                && (column.path && Math.abs(offset - pathCenter) < 2.4 + patch
                    || !column.path && patch > -.25);
        // Protect the entire approach, spawn and boat lane, including overhead clearance.
        if (column.path || Math.abs(offset) < 13 || z < START_Z + 8)
            return new Details(mud, 0, 0, 0, false);
        int cellX = Math.floorDiv(x, 9), cellZ = Math.floorDiv(z, 9);
        long cell = hash(seed ^ 0x571CE, cellX, cellZ);
        int rootX = cellX * 9 + 2 + (int)Math.floorMod(cell, 5);
        int rootZ = cellZ * 9 + 2 + (int)Math.floorMod(cell >>> 8, 5);
        int radius = Math.max(Math.abs(x - rootX), Math.abs(z - rootZ));
        boolean cluster = (cell & 3) != 0;
        int rock = cluster && radius <= 2 && column.floor >= WATER_Y - 3
                ? Math.max(0, 3 - radius - (int)(hash(seed, x, z) & 1)) : 0;
        int spike = cluster && radius == 0 && rock > 0 ? 2 + (int)((cell >>> 16) & 7) : 0;
        int hanging = cluster && radius == 0 ? 3 + (int)((cell >>> 24) & 7) : 0;
        int clearance = Math.max(0, column.roof - column.floor - 5);
        rock = Math.min(rock, clearance);
        spike = Math.min(spike, Math.max(0, clearance - rock));
        hanging = Math.min(hanging, Math.max(0, clearance - rock - spike));
        // Thin wall-fed ribbons; bounded falling states avoid cascades of fluid updates.
        int fallZ = Math.floorMod(z + 41, 67);
        boolean waterfall = z > -110 && z < 25 && fallZ < 2
                && offset > tunnelWidth(seed, z) - 4 && offset < tunnelWidth(seed, z) - 2;
        return new Details(mud, rock, spike, hanging, waterfall);
    }

    /** Ruined stacked dwellings emerge from the walls like a termite nest. */
    private static BlockState settlement(int x, int y, int z, Column column) {
        if (z < -158 || z > -30 || column.path) return null;
        int segment = Math.floorDiv(z + 158, 64);
        int dz = Math.floorMod(z + 158, 64) - 18;
        if (Math.abs(dz) > 10) return null;
        double offset = (x - riverCenter(z)) * (segment % 2 == 0 ? 1 : -1);
        if (offset < 22 || offset > 33) return null;
        int height = y - (WATER_Y + 7);
        if (height < 0 || height > 28) return null;
        int tier = height / 7;
        double facade = 22 + tier * 1.3 + Math.abs(dz) * .18;
        if (offset < facade) return null;
        int course = height % 7;
        if (course == 0) return Asterion.SHADED_SHALE_BRICKS.defaultBlockState();
        // Recessed doorways and windows, with occasional warm points of light.
        boolean opening = Math.floorMod(dz + tier * 3, 6) < 2 && course >= 2 && course <= 4;
        if (offset < facade + 1.2 && !opening) return Asterion.SHALE_BRICKS.defaultBlockState();
        if (offset > facade + 4) return Asterion.SHADED_SHALE_BRICKS.defaultBlockState();
        if (opening && course == 3 && offset > facade + 3 && (tier + dz & 3) == 0)
            return Blocks.SHROOMLIGHT.defaultBlockState();
        return Blocks.AIR.defaultBlockState();
    }

    private record Details(boolean mud, int rock, int spike, int hanging, boolean waterfall) { }

    private static boolean atmosphericLight(int x, int y, int z, Column column) {
        return column.path && y == column.floor + 3 && Math.floorMod(z, 24) == 8
                && Math.abs(x - (riverCenter(z) - 18)) < .55;
    }

    /** Four seeded octaves, sampled per column; continuous across chunk boundaries. */
    private static double octaves(long seed, double x, double z) {
        double sum = 0, amplitude = 1, weight = 0;
        for (int octave = 0; octave < 4; octave++) {
            int ix = (int)Math.floor(x), iz = (int)Math.floor(z);
            double fx = x - ix, fz = z - iz;
            fx = fx * fx * fx * (fx * (fx * 6 - 15) + 10);
            fz = fz * fz * fz * (fz * (fz * 6 - 15) + 10);
            long s = seed + octave * 0x9E3779B97F4A7C15L;
            double a = (hash(s, ix, iz) >>> 11) * 0x1.0p-53;
            double b = (hash(s, ix + 1, iz) >>> 11) * 0x1.0p-53;
            double c = (hash(s, ix, iz + 1) >>> 11) * 0x1.0p-53;
            double d = (hash(s, ix + 1, iz + 1) >>> 11) * 0x1.0p-53;
            sum += ((a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz) * amplitude;
            weight += amplitude;
            amplitude *= .5;
            x *= 2; z *= 2;
        }
        return sum / weight * 2 - 1;
    }

    private static double smooth(double value) {
        value = Math.clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }

    private static long hash(long seed, int x, int z) {
        long value = seed ^ (long)x * 0x9E3779B97F4A7C15L ^ (long)z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private record Column(boolean open, int floor, int roof, boolean path) { }
}
