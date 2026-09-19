package net.krodark.asterion.update.underworld.world;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
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
                BlockState stone = (shaded ? Asterion.SHADED_SHALE : Asterion.SHALE).defaultBlockState();
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
        double width = 24 + 4 * octaves(seed, z * .016, 0) + mouth * 24;
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
        double arch = Math.sqrt(Math.max(0, 1 - lateral * lateral / (width * width)));
        double roof = WATER_Y + 8 + arch * (27 + mouth * 36
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
