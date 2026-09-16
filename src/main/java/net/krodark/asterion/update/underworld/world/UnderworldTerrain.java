package net.krodark.asterion.update.underworld.world;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Deterministic authored-procedural river corridor: finite, enclosed and readable as one journey. */
public final class UnderworldTerrain {
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 127;
    public static final int WATER_Y = 47;
    public static final int START_Z = -32;
    public static final int END_Z = 1024;
    public static final int SPAWN_X = 21;
    public static final int SPAWN_Y = 51;
    public static final int SPAWN_Z = 20;
    public static final int FERRY_Z = 58;

    private UnderworldTerrain() { }

    public static double riverCenter(double z) {
        return Math.sin(z * .008) * 18.0
                + Math.sin(z * .019 + 1.7) * 9.0
                + Math.sin(z * .043 + .4) * 4.0;
    }

    public static void generate(ChunkAccess chunk, long seed) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                Column column = column(seed, x, z);
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    BlockState state;
                    if (y == MIN_Y || y == MAX_Y) state = Blocks.BEDROCK.defaultBlockState();
                    else if (!column.open || y <= column.floor || y >= column.roof)
                        state = y == column.floor ? groundRock(seed, x, y, z) : rock(seed, x, y, z);
                    else if (column.river && y <= WATER_Y)
                        state = Blocks.WATER.defaultBlockState();
                    else if (atmosphericLight(x, y, z, column))
                        state = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL,
                                Math.floorMod(z, 48) == 10 ? 12 : 10);
                    else state = Blocks.AIR.defaultBlockState();
                    chunk.setBlockState(pos.set(x, y, z), state, 0);
                }
            }
        }
    }

    private static Column column(long seed, int x, int z) {
        double center = riverCenter(z);
        double lateral = Math.abs(x - center);
        double endDistance = Math.hypot(x - riverCenter(END_Z - 32), (z - (END_Z - 32)) * .72);
        double startDistance = Math.hypot(x - riverCenter(18), (z - 18) * .82);
        double endChamber = Math.max(0, 1 - endDistance / 62.0);
        double arrivalPocket = Math.max(0, 1 - startDistance / 34.0);
        boolean withinJourney = z >= START_Z && z <= END_Z;
        // A close arrival pocket opens into alternating narrows and halls instead of one broad tube.
        double width = 25 + 7 * wave(z * .017, seed)
                + 6 * wave(z * .047, seed ^ 0x771L)
                + arrivalPocket * 3 + endChamber * 24;
        boolean open = withinJourney && lateral < width;
        double riverWidth = 8.5 + 4.2 * wave(z * .031, seed ^ 0x71AEL)
                + 2.3 * wave(z * .083, seed ^ 0xBEEFL) + endChamber * 5;
        boolean river = open && lateral < riverWidth && z > 34;

        double bank = smooth((lateral - riverWidth + 2.5) / 8.0);
        double floor = 39 + wave(x * .11 + z * .019, seed ^ 0xF100DL) * 2.2 + bank * 10.0;
        if (z < 44) floor = Math.max(floor, 49 + wave(x * .13, seed) * 1.2);
        double hell = smooth((z - 220.0) / 720.0);
        double roof = 68 + 15 * wave(z * .009, seed ^ 0xC4A7EL)
                + 11 * wave(x * .024 + z * .013, seed ^ 0x5A1EL) + endChamber * 25;
        if (z < 52) roof = Math.min(roof, 65 + 5 * arrivalPocket);

        // Large shale teeth create peaks without interrupting the navigable channel.
        long cell = hash(seed ^ 0x51A6EL, Math.floorDiv(x, 13), Math.floorDiv(z, 17));
        double px = Math.floorDiv(x, 13) * 13 + 3 + Math.floorMod(cell >>> 8, 8);
        double pz = Math.floorDiv(z, 17) * 17 + 4 + Math.floorMod(cell >>> 16, 9);
        double radius = 3.2 + Math.floorMod(cell >>> 24, 5);
        double peak = Math.max(0, 1 - Math.hypot(x - px, z - pz) / radius);
        double peakScale = 1.0 + hell * .85;
        if (lateral > riverWidth + 2 && (cell & 3L) == 0)
            floor += peak * (10 + Math.floorMod(cell >>> 32, 21)) * peakScale;
        if (lateral > riverWidth - 2 && (cell & 12L) == 4)
            roof -= peak * (10 + Math.floorMod(cell >>> 39, 24)) * peakScale;
        // A second, tighter field creates the sharp silhouettes visible downriver.
        long toothCell = hash(seed ^ 0x6E11L, Math.floorDiv(x, 9), Math.floorDiv(z, 11));
        double tx = Math.floorDiv(x, 9) * 9 + 2 + Math.floorMod(toothCell >>> 9, 6);
        double tz = Math.floorDiv(z, 11) * 11 + 2 + Math.floorMod(toothCell >>> 18, 7);
        double tooth = Math.max(0, 1 - Math.hypot(x - tx, z - tz) / (2.2 + hell * 1.8));
        if (lateral > riverWidth + 1 && (toothCell & 7L) == 1)
            floor += tooth * (7 + Math.floorMod(toothCell >>> 28, 13)) * peakScale;
        if (lateral > riverWidth - 1 && (toothCell & 7L) == 3)
            roof -= tooth * (8 + Math.floorMod(toothCell >>> 36, 15)) * peakScale;
        if (roof - floor < 6) open = false;
        return new Column(open, river, (int)Math.floor(floor), (int)Math.ceil(roof), riverWidth);
    }

    private static boolean atmosphericLight(int x, int y, int z, Column column) {
        if (!column.open || y < column.floor + 3 || y >= column.roof - 2) return false;
        double center = riverCenter(z);
        int phase = Math.floorMod(z, 48);
        if (phase == 10) {
            double bank = center + (((z / 48) & 1) == 0 ? 1 : -1) * (column.riverWidth + 4.5);
            return y == column.floor + 4 && Math.abs(x - bank) < .5;
        }
        // Overhead glow catches the low mist and silhouettes the increasingly sharp teeth.
        return phase == 34 && y == WATER_Y + 7 && Math.abs(x - center) < .5;
    }

    private static BlockState rock(long seed, int x, int y, int z) {
        long grain = hash(seed ^ y * 0x51EDL, x, z);
        double hell = smooth((z - 250.0) / 700.0);
        if (hell > .05 && Math.floorMod(grain >>> 9, 23) < 2 + (int)(hell * 5))
            return ((grain & 1L) == 0 ? Blocks.BLACKSTONE : Blocks.BASALT).defaultBlockState();
        Block block = (grain & 15L) < 5 ? Asterion.SHADED_SHALE : Asterion.SHALE;
        return block.defaultBlockState();
    }

    private static BlockState groundRock(long seed, int x, int y, int z) {
        long grain = hash(seed ^ 0xAE11L ^ y * 0x51EDL, x, z);
        double hell = smooth((z - 300.0) / 640.0);
        if (hell > .2 && Math.floorMod(grain, 97) < 1 + (int)(hell * 4))
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (hell > .08 && Math.floorMod(grain >>> 7, 17) < 2 + (int)(hell * 3))
            return ((grain & 1L) == 0 ? Blocks.BLACKSTONE : Blocks.BASALT).defaultBlockState();
        return rock(seed, x, y, z);
    }

    private static double wave(double coordinate, long seed) {
        return .5 + .5 * Math.sin(coordinate + (seed & 1023L) * .0017);
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

    private record Column(boolean open, boolean river, int floor, int roof, double riverWidth) { }
}
