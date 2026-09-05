package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Connected, rounded chambers under the Forge. Every sample uses world coordinates. */
public final class ShaleCaves {
    private ShaleCaves() {}
    private record Column(double floor, double roof, double clearance) {}

    public static boolean contains(BlockPos pos) {
        return pos.getY() > LabyrinthLevels.CAVE_BOTTOM_Y + 3 && pos.getY() <= LabyrinthLevels.CAVE_ROOF_Y;
    }

    private static double phase(long seed) { return (seed & 65535) / 65535.0 * Math.PI * 2; }

    private record Chamber(double x, double z, double radius, double stretch) {}

    private static double unit(long seed, int x, int z) {
        return (CatacombLayout.hash(seed, x, z) >>> 11) * 0x1.0p-53;
    }

    private static double noise(long seed, double x, double z) {
        int ix = (int)Math.floor(x), iz = (int)Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double north = unit(seed, ix, iz) * (1 - fx) + unit(seed, ix + 1, iz) * fx;
        double south = unit(seed, ix, iz + 1) * (1 - fx) + unit(seed, ix + 1, iz + 1) * fx;
        return north * (1 - fz) + south * fz;
    }

    private static Chamber chamber(long seed, int x, int z) {
        return new Chamber(x * 64 + (unit(seed, x, z) - .5) * 28,
                z * 64 + (unit(seed ^ 371, x, z) - .5) * 28,
                13 + unit(seed ^ 817, x, z) * 17, .7 + unit(seed ^ 991, x, z) * .7);
    }

    private static double passage(double x, double z, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        double t = Math.clamp(((x - ax) * vx + (z - az) * vz) / Math.max(1, vx * vx + vz * vz), 0, 1);
        return Math.hypot(x - ax - vx * t, z - az - vz * t);
    }

    private static double ground(long seed, double x, double z) {
        return -57 + noise(seed ^ 743, x / 100, z / 100) * 48
                + noise(seed ^ 189, x / 43, z / 43) * 7;
    }

    private static Column column(long seed, int x, int z) {
        double wx = x + (noise(seed ^ 41, x / 38.0, z / 38.0) - .5) * 10;
        double wz = z + (noise(seed ^ 87, x / 43.0, z / 43.0) - .5) * 10;
        int gx = (int)Math.floor(wx / 64), gz = (int)Math.floor(wz / 64);
        double clearance = -100, nearest = Double.MAX_VALUE;
        Chamber closest = null;
        double width = 2.2 + noise(seed ^ 619, x / 27.0, z / 27.0) * 5.5;
        for (int cx = gx - 1; cx <= gx + 1; cx++) for (int cz = gz - 1; cz <= gz + 1; cz++) {
            Chamber room = chamber(seed, cx, cz);
            double distance = Math.hypot(wx - room.x, (wz - room.z) * room.stretch);
            if (distance < nearest) { nearest = distance; closest = room; }
            clearance = Math.max(clearance, room.radius - distance);
            Chamber east = chamber(seed, cx + 1, cz), south = chamber(seed, cx, cz + 1);
            clearance = Math.max(clearance, width - passage(wx, wz, room.x, room.z, east.x, east.z));
            clearance = Math.max(clearance, width - passage(wx, wz, room.x, room.z, south.x, south.z));
        }
        // Flatten chamber centres, with a smooth transition into the sloping passages.
        double flat = Math.clamp((.85 - nearest / closest.radius) / .5, 0, 1);
        flat = flat * flat * (3 - 2 * flat);
        double floor = ground(seed, x, z) * (1 - flat) + Math.rint(ground(seed, closest.x, closest.z) / 3) * 3 * flat;
        int cx = AuthoredForge.districtCenter(x), cz = AuthoredForge.districtCenter(z);
        int shaftX = cx - 39;
        Chamber landing = chamber(seed, (int)Math.round(shaftX / 64.0), (int)Math.round(cz / 64.0));
        clearance = Math.max(clearance, 16 - Math.hypot(x - shaftX, z - cz));
        clearance = Math.max(clearance, 6 - passage(x, z, shaftX, cz, landing.x, landing.z));
        double chamberSpace = Math.clamp((closest.radius - nearest) / 9, 0, 1);
        double height = 4.5 + noise(seed ^ 6197, x / 58.0, z / 58.0) * 4
                + chamberSpace * (10 + noise(seed ^ 379, x / 83.0, z / 83.0) * 15);
        height = Math.min(height, LabyrinthLevels.CAVE_ROOF_Y - 1 - floor);
        double round = Math.sqrt(Math.clamp(clearance / 7, 0, 1));
        return new Column(floor + height * (1 - round) * .5, floor + height * (1 + round) * .5, clearance);
    }

    public static int floorY(long seed, int x, int z) { return (int)Math.floor(column(seed, x, z).floor); }

    public static void generate(ChunkAccess chunk, long seed) {
        if (chunk.getMinY() > LabyrinthLevels.CAVE_BOTTOM_Y) return;
        var pos = new BlockPos.MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        // A one-block border supplies all slope and puddle neighbours without resampling
        // the chamber graph several times for every floor block.
        Column[][] columns = new Column[18][18];
        for (int dx = 0; dx < 18; dx++) for (int dz = 0; dz < 18; dz++)
            columns[dx][dz] = column(seed, minX + dx - 1, minZ + dz - 1);
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++)
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                int dx = x - minX + 1, dz = z - minZ + 1;
                Column cave = columns[dx][dz];
                int floor = (int)Math.floor(cave.floor), roof = (int)Math.ceil(cave.roof);
                boolean open = cave.clearance > .3 && roof - floor >= 4;
                boolean puddle = open && cave.clearance > 5 && wet(seed, x, z)
                        && columns[dx - 1][dz].floor >= floor && columns[dx + 1][dz].floor >= floor
                        && columns[dx][dz - 1].floor >= floor && columns[dx][dz + 1].floor >= floor;
                for (int y = LabyrinthLevels.CAVE_BOTTOM_Y; y <= LabyrinthLevels.CAVE_ROOF_Y; y++) {
                    BlockState state;
                    if (y <= LabyrinthLevels.CAVE_BOTTOM_Y + 2) state = Blocks.BEDROCK.defaultBlockState();
                    else if (open && y > floor && y < roof) state = Blocks.AIR.defaultBlockState();
                    else {
                        state = rock(seed, x, y, z);
                        if (open && y == floor) {
                            if (puddle) state = Blocks.WATER.defaultBlockState();
                            else if (!CatacombProtection.isOre(state)) state = smoothFloor(seed, x, z, columns, dx, dz, state);
                        } else if (open && y == roof && !CatacombProtection.isOre(state)
                                && cave.roof - Math.floor(cave.roof) < .5)
                            state = slab(shaded(seed, x, y, z)).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
                    }
                    // Keep the old terrain-generation sentinel, buried in the roof rock.
                    if (y == 1 && x == chunk.getPos().getMinBlockX() && z == chunk.getPos().getMinBlockZ())
                        state = Blocks.BEDROCK.defaultBlockState();
                    chunk.setBlockState(pos.set(x, y, z), state, 0);
                }
                if (open && !puddle && cave.clearance > 4
                        && noise(seed ^ 7119, x / 31.0, z / 31.0) > .56) {
                    if (!CatacombProtection.isOre(chunk.getBlockState(pos.set(x, floor, z)))) {
                        chunk.setBlockState(pos, Asterion.ANCIENT_MOSS.defaultBlockState(), 0);
                        long plant = CatacombLayout.hash(seed ^ 727, x, z);
                        if (Math.floorMod(plant, 9) == 0)
                            chunk.setBlockState(pos.set(x, floor + 1, z), Asterion.ANCIENT_MOSS_CARPET.defaultBlockState(), 0);
                        else if (Math.floorMod(plant, 23) == 0)
                            chunk.setBlockState(pos.set(x, floor + 1, z), Blocks.BROWN_MUSHROOM.defaultBlockState(), 0);
                    }
                }
                if (open && !puddle) spikes(chunk, seed, x, z, floor, roof, cave.clearance);
                if (open && roof + 2 <= LabyrinthLevels.CAVE_ROOF_Y && wet(seed ^ 0xD21FL, x, z)
                        && Math.floorMod(CatacombLayout.hash(seed, x, z), 5) == 0) {
                    // A sealed water pocket above one full ceiling block produces vanilla drips.
                    chunk.setBlockState(pos.set(x, roof, z), base(shaded(seed, x, roof, z)).defaultBlockState(), 0);
                    chunk.setBlockState(pos.set(x, roof + 1, z), Blocks.WATER.defaultBlockState(), 0);
                }
            }
        chunk.setBlockState(marker(chunk), revision(), 0);
    }

    public static void repairEmptyChunk(net.minecraft.world.level.chunk.LevelChunk chunk, long seed) {
        if (chunk.getMinY() > LabyrinthLevels.CAVE_BOTTOM_Y || chunk.getBlockState(marker(chunk)).equals(revision())) return;
        for (BlockPos pos : BlockPos.betweenClosed(chunk.getPos().getMinBlockX(), LabyrinthLevels.CAVE_BOTTOM_Y,
                chunk.getPos().getMinBlockZ(), chunk.getPos().getMaxBlockX(), LabyrinthLevels.CAVE_ROOF_Y,
                chunk.getPos().getMaxBlockZ())) {
            var state = chunk.getBlockState(pos);
            if (!state.isAir() && !(pos.getY() == 1 && state.is(Blocks.BEDROCK))) return;
        }
        generate(chunk, seed);
        chunk.markUnsaved();
    }

    private static BlockPos marker(ChunkAccess chunk) {
        return new BlockPos(chunk.getPos().getMinBlockX(), LabyrinthLevels.CAVE_BOTTOM_Y + 1, chunk.getPos().getMinBlockZ());
    }
    private static BlockState revision() { return Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 1); }

    private static boolean wet(long seed, int x, int z) {
        return noise(seed ^ 751, x / 23.0, z / 23.0) > .64
                && noise(seed ^ 929, x / 7.0, z / 7.0) > .4;
    }

    private static boolean shaded(long seed, int x, int y, int z) {
        double band = 7 * Math.sin(x * .055 + phase(seed)) + 5 * Math.cos(z * .06 - phase(seed));
        return y < -24 + band;
    }

    private static BlockState rock(long seed, int x, int y, int z) {
        boolean dark = shaded(seed, x, y, z);
        long vein = CatacombLayout.hash(seed ^ Math.floorDiv(y, 4) * 0x51EDL, Math.floorDiv(x, 4), Math.floorDiv(z, 4));
        if (Math.floorMod(vein, 17) == 0 && Math.floorMod(x, 4) != 3 && Math.floorMod(z, 4) != 3 && Math.floorMod(y, 4) != 3) {
            boolean celestial = Math.floorMod(vein >>> 8, 4) == 0;
            Block ore = celestial
                    ? (dark ? Asterion.SHADED_SHALE_CELESTIAL_GOLD_ORE : Asterion.SHALE_CELESTIAL_GOLD_ORE)
                    : (dark ? Asterion.SHADED_SHALE_TARNISHED_GOLD_ORE : Asterion.SHALE_TARNISHED_GOLD_ORE);
            return ore.defaultBlockState();
        }
        return base(dark).defaultBlockState();
    }

    private static BlockState smoothFloor(long seed, int x, int z, Column[][] columns, int dx, int dz, BlockState fallback) {
        double height = columns[dx][dz].floor;
        boolean dark = shaded(seed, x, (int)height, z);
        double fraction = height - Math.floor(height);
        if (fraction < .02) return fallback;
        if (fraction < .3) return slab(dark).defaultBlockState();
        if (fraction > .8) return fallback;
        Direction uphill = Direction.NORTH;
        double best = height;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            double neighbor = columns[dx + direction.getStepX()][dz + direction.getStepZ()].floor;
            if (neighbor > best) { best = neighbor; uphill = direction; }
        }
        return stairs(dark).defaultBlockState().setValue(StairBlock.FACING, uphill);
    }

    private static void spikes(ChunkAccess chunk, long seed, int x, int z, int floor, int roof, double clearance) {
        long roll = CatacombLayout.hash(seed ^ 0x51A6EL, Math.floorDiv(x, 11), Math.floorDiv(z, 11));
        if (Math.floorMod(roll, 3) != 0 || clearance < 8 || roof - floor < 9) return;
        double distance = Math.hypot(Math.floorMod(x, 11) - (2 + Math.floorMod(roll >>> 8, 7)),
                Math.floorMod(z, 11) - (2 + Math.floorMod(roll >>> 16, 7)));
        int height = (int)Math.floor(3 + Math.floorMod(roll >>> 24, 4) - distance * 1.7);
        if (height <= 0) return;
        boolean hanging = (roll & 1) != 0;
        for (int offset = 1; offset <= height; offset++) {
            int y = hanging ? roof - offset : floor + offset;
            boolean dark = shaded(seed, x, y, z);
            BlockState state = base(dark).defaultBlockState();
            if (offset == height) state = (dark ? Asterion.SHADED_SHALE_WALL : Asterion.SHALE_WALL).defaultBlockState();
            else if (offset == height - 1) state = stairs(dark).defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.from2DDataValue((int)(roll & 3)))
                    .setValue(StairBlock.HALF, hanging ? Half.TOP : Half.BOTTOM);
            chunk.setBlockState(new BlockPos(x, y, z), state, 0);
        }
    }

    private static Block base(boolean shaded) { return shaded ? Asterion.SHADED_SHALE : Asterion.SHALE; }
    private static Block slab(boolean shaded) { return shaded ? Asterion.SHADED_SHALE_SLAB : Asterion.SHALE_SLAB; }
    private static Block stairs(boolean shaded) { return shaded ? Asterion.SHADED_SHALE_STAIRS : Asterion.SHALE_STAIRS; }
}
