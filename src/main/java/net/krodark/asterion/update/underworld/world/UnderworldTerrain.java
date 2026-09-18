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
    public static final int MAX_Y = 159;
    public static final int WATER_Y = 47;
    public static final int START_Z = -32;
    public static final int END_Z = 1024;
    public static final int SPAWN_X = -220;
    public static final int SPAWN_Y = 40;
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
                // Material noise is evaluated once per column, not for every buried block.
                boolean shaded = octaves(seed ^ 0x5ADE, x * .019, z * .019) > .08;
                BlockState stone = (shaded ? Asterion.SHADED_SHALE : Asterion.SHALE).defaultBlockState();
                BlockState slab = (shaded ? Asterion.SHADED_SHALE_SLAB : Asterion.SHALE_SLAB).defaultBlockState();
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    BlockState state;
                    if (y == MIN_Y || y == MAX_Y) state = Blocks.BEDROCK.defaultBlockState();
                    else if (!column.open || y <= column.floor || y >= column.roof)
                        state = stone;
                    else if (column.river && y <= WATER_Y)
                        state = Blocks.WATER.defaultBlockState();
                    else if (y == column.floor + 1 && column.halfStep && !column.river)
                        state = slab;
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
        double width = 32 + 10 * octaves(seed, x * .012, z * .012)
                + arrivalPocket * 3 + endChamber * 24;
        boolean riverCavern = withinJourney && lateral < width;
        double riverWidth = 8.5 + 4.2 * wave(z * .031, seed ^ 0x71AEL)
                + 2.3 * wave(z * .083, seed ^ 0xBEEFL) + endChamber * 5;
        boolean river = riverCavern && lateral < riverWidth && z > 34;

        double bank = smooth((lateral - riverWidth + 2.5) / 8.0);
        double floor = 40 + octaves(seed ^ 0xF100DL, x * .035, z * .035) * 3.0 + bank * 10.0;
        double hell = smooth((z - 220.0) / 720.0);
        double roof = 84 + 20 * octaves(seed ^ 0xC4A7EL, x * .015, z * .015) + endChamber * 25;
        // Elliptical vaults taper continuously into the banks instead of ending in vertical cuts.
        double vault = Math.sqrt(Math.max(0.0, 1.0 - Math.pow(lateral / width, 2.0)));
        roof = floor + 5.0 + (roof - floor - 5.0) * vault;
        if (riverCavern) floor += 7.0 * smooth((lateral / width - .68) / .32);
        if (z < 52) roof = Math.max(roof, 112 + 18 * arrivalPocket);

        // Three old-life wombs and their tall approach tunnels feed the same river threshold.
        // The middle one is the player's first-death arrival; the others leave room for cities.
        boolean birthChamber = false;
        boolean approachTunnel = false;
        int[] lanes = {-170, 20, 210};
        for (int lane = 0; lane < lanes.length; lane++) {
            double chamberX = -220.0;
            double chamberZ = lanes[lane];
            double chamberDistance = Math.hypot(x - chamberX, z - chamberZ)
                    + 8 * octaves(seed ^ (lane * 0x911L), x * .018, z * .018);
            double chamber = Math.max(0.0, 1.0 - chamberDistance / 100.0);
            if (chamber > 0.0) {
                birthChamber = true;
                double chamberFloor = 35.0 + wave(x * .045 + z * .031, seed ^ (lane * 0x911L)) * 2.2
                        + 14.0 * smooth((chamberDistance - 78.0) / 22.0);
                double dome = Math.sqrt(Math.max(0.0, 1.0 - Math.pow(chamberDistance / 103.0, 2.0)));
                floor = riverCavern ? Math.min(floor, chamberFloor) : chamberFloor;
                roof = riverCavern ? Math.max(roof, chamberFloor + 7.0 + dome * 105.0)
                        : chamberFloor + 7.0 + dome * 105.0;
            }

            double tunnelStart = -132.0;
            double tunnelEnd = center - 18.0;
            if (x >= tunnelStart && x <= tunnelEnd) {
                double progress = smooth((x - tunnelStart) / Math.max(1.0, tunnelEnd - tunnelStart));
                double targetZ = 48.0 + (lane - 1) * 9.0;
                double tunnelZ = chamberZ + (targetZ - chamberZ) * progress
                        + Math.sin(progress * Math.PI * 2.0 + lane * 1.9) * 10.0 * (1.0 - progress);
                double tunnelHalfWidth = 25.0 + smooth((progress - .72) / .28) * 10.0
                        + 5 * octaves(seed ^ (lane * 0x551L), x * .023, z * .023);
                if (Math.abs(z - tunnelZ) < tunnelHalfWidth) {
                    approachTunnel = true;
                    double tunnelFloor = 37.0 + wave(x * .035 + z * .018, seed ^ (lane * 0x551L)) * 2.0;
                    double arch = Math.sqrt(Math.max(0.0, 1.0
                            - Math.pow((z - tunnelZ) / tunnelHalfWidth, 2.0)));
                    double tunnelRoof = tunnelFloor + 8.0 + arch * (92.0
                            + 8.0 * wave(x * .019 - z * .013, seed ^ 0x70A1L));
                    floor = birthChamber || riverCavern ? Math.min(floor, tunnelFloor) : tunnelFloor;
                    roof = birthChamber || riverCavern ? Math.max(roof, tunnelRoof) : tunnelRoof;
                }
            }
        }

        double bayDistance = Math.hypot((x - center) * .78, z - 48.0);
        boolean arrivalBay = bayDistance < 68.0;
        if (arrivalBay) {
            double bay = 1.0 - bayDistance / 68.0;
            floor = Math.min(floor, 38.0 + (1.0 - bay) * 5.0);
            roof = Math.max(roof, 94.0 + bay * 38.0);
        }
        boolean open = riverCavern || birthChamber || approachTunnel || arrivalBay;

        // Side galleries follow continuous noisy curves; never cut through the river bed.
        if (!open && x < center - riverWidth - 10 && x > -340 && z > -260 && z < 360) {
            double gallery = 110 + 55 * octaves(seed ^ 0xCA7E, x * .009, 1.7);
            double distance = Math.abs(z - gallery);
            double radius = 7 + 3 * octaves(seed ^ 0x731, x * .031, z * .031);
            if (distance < radius) {
                open = true;
                floor = 39 + 3 * octaves(seed, x * .025, z * .025);
                roof = floor + 3 + 17 * Math.sqrt(Math.max(0, 1 - distance * distance / (radius * radius)));
            }
        }
        // Closed bowls and ridges, without cell-local spikes or square cutoffs.
        if (open && !riverCavern) {
            double depression = Math.max(0, octaves(seed ^ 0xC2A7E, x * .032, z * .032) - .18);
            floor -= depression * 9;
        }
        // The shore is a single continuous height field. Every low river column
        // is filled to the same waterline; dry adjacent columns meet that waterline.
        if (open && z >= 34 && z <= END_Z) {
            double edge = lateral - riverWidth;
            double shore = WATER_Y + 1.0 + 1.5 * edge;
            if (edge < 0 && z > 34 && z < END_Z) {
                double endBank = Math.min(smooth((z - 34.0) / 10), smooth((END_Z - z) / 10.0));
                floor = WATER_Y - 10 + 10 * smooth(lateral / riverWidth);
                floor = Math.max(floor, WATER_Y - 10 * endBank);
                river = true;
            } else {
                floor = Math.max(floor, Math.max(WATER_Y + 1, Math.min(WATER_Y + 5, shore)));
                river = false;
            }
        }
        // A gently climbing, illuminated approach ends at a shale boarding pier.
        double routeT = smooth((x - SPAWN_X) / (riverCenter(FERRY_Z) - SPAWN_X));
        double routeZ = SPAWN_Z + (FERRY_Z - SPAWN_Z) * routeT;
        if (x >= SPAWN_X - 5 && x < center - 1.5 && Math.abs(z - routeZ) < 2.4) {
            open = true;
            river = false;
            floor = 38 + 10 * routeT;
            roof = Math.max(roof, floor + 8);
        }
        if (roof - floor < 6) open = false;
        boolean riverBank = z > 34 && Math.abs(lateral - riverWidth) < 7.5;
        return new Column(open, river, birthChamber, approachTunnel, riverBank,
                (int)Math.floor(floor), (int)Math.ceil(roof), riverWidth, floor - Math.floor(floor) > .45);
    }

    private static boolean atmosphericLight(int x, int y, int z, Column column) {
        if (!column.open || y < column.floor + 3 || y >= column.roof - 2) return false;
        double routeT = smooth((x - SPAWN_X) / (riverCenter(FERRY_Z) - SPAWN_X));
        double routeZ = SPAWN_Z + (FERRY_Z - SPAWN_Z) * routeT;
        if (x >= SPAWN_X && x < riverCenter(FERRY_Z) && Math.floorMod(x, 9) == 0
                && Math.abs(z - routeZ) < 1.0) return y == column.floor + 3;
        if (column.approachTunnel && Math.floorMod(x, 18) == 4 && Math.floorMod(z, 18) == 7)
            return y == column.floor + 7;
        double center = riverCenter(z);
        int phase = Math.floorMod(z, 48);
        if (phase == 10) {
            double bank = center + (((z / 48) & 1) == 0 ? 1 : -1) * (column.riverWidth + 4.5);
            return y == column.floor + 4 && Math.abs(x - bank) < .5;
        }
        // Overhead glow catches the low mist and silhouettes the increasingly sharp teeth.
        return phase == 34 && y == WATER_Y + 7 && Math.abs(x - center) < .5;
    }

    private static double wave(double coordinate, long seed) {
        return .5 + .5 * Math.sin(coordinate + (seed & 1023L) * .0017);
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

    private record Column(boolean open, boolean river, boolean birthChamber, boolean approachTunnel, boolean riverBank,
                          int floor, int roof, double riverWidth, boolean halfStep) { }
}
