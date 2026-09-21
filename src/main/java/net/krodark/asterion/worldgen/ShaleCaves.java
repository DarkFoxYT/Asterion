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

 
public final class ShaleCaves {
    private ShaleCaves() {}
    private record Column(double floor, double roof, double clearance) {}

    public static boolean contains(BlockPos pos) {
        return pos.getY() > LabyrinthLevels.CAVE_BOTTOM_Y + 3 && pos.getY() <= LabyrinthLevels.CAVE_ROOF_Y;
    }

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
        double scale = unit(seed ^ 0xC4A7E2L, x, z) > .87 ? 1.65 : 1.0;
        return new Chamber(x * 64 + (unit(seed, x, z) - .5) * 28,
                z * 64 + (unit(seed ^ 371, x, z) - .5) * 28,
                (13 + unit(seed ^ 817, x, z) * 17) * scale,
                (.7 + unit(seed ^ 991, x, z) * .7) / Math.sqrt(scale));
    }

    private static Chamber chamber(long seed, int x, int z, java.util.Map<Long, Chamber> chambers) {
        if (chambers == null) return chamber(seed, x, z);
        long key = net.minecraft.world.level.ChunkPos.pack(x, z);
        return chambers.computeIfAbsent(key, ignored -> chamber(seed, x, z));
    }

    private static double passage(double x, double z, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        double t = Math.clamp(((x - ax) * vx + (z - az) * vz) / Math.max(1, vx * vx + vz * vz), 0, 1);
        return Math.hypot(x - ax - vx * t, z - az - vz * t);
    }

    private static double ground(long seed, double x, double z) {
        return -57 + noise(seed ^ 743, x / 140, z / 140) * 42
                + noise(seed ^ 189, x / 64, z / 64) * 3;
    }

    private static Column column(long seed, int x, int z) {
        return column(seed, x, z, null);
    }

    private static Column column(long seed, int x, int z, java.util.Map<Long, Chamber> chambers) {
        double wx = x + (noise(seed ^ 41, x / 38.0, z / 38.0) - .5) * 10;
        double wz = z + (noise(seed ^ 87, x / 43.0, z / 43.0) - .5) * 10;
        int gx = (int)Math.floor(wx / 64), gz = (int)Math.floor(wz / 64);
        double clearance = -100, nearest = Double.MAX_VALUE;
        Chamber closest = null;
        double width = 2.2 + noise(seed ^ 619, x / 27.0, z / 27.0) * 5.5;
        for (int cx = gx - 1; cx <= gx + 1; cx++) for (int cz = gz - 1; cz <= gz + 1; cz++) {
            Chamber room = chamber(seed, cx, cz, chambers);
            double distance = Math.hypot(wx - room.x, (wz - room.z) * room.stretch);
            if (distance < nearest) { nearest = distance; closest = room; }
            clearance = Math.max(clearance, room.radius - distance);
            Chamber east = chamber(seed, cx + 1, cz, chambers), south = chamber(seed, cx, cz + 1, chambers);
            clearance = Math.max(clearance, width - passage(wx, wz, room.x, room.z, east.x, east.z));
            clearance = Math.max(clearance, width - passage(wx, wz, room.x, room.z, south.x, south.z));
             
             
            long branch = CatacombLayout.hash(seed ^ 0x7A11E15L, cx, cz);
            int bx = cx + (((branch & 1L) == 0) ? 1 : -1);
            int bz = cz + (((branch & 2L) == 0) ? 1 : -1);
            Chamber diagonal = chamber(seed, bx, bz, chambers);
            double branchWidth = 2.0 + Math.floorMod(branch >>> 8, 6);
            clearance = Math.max(clearance,
                    branchWidth - passage(wx, wz, room.x, room.z, diagonal.x, diagonal.z));
        }
         
        double flat = Math.clamp((.85 - nearest / closest.radius) / .5, 0, 1);
        flat = flat * flat * (3 - 2 * flat);
        double floor = ground(seed, x, z) * (1 - flat) + Math.rint(ground(seed, closest.x, closest.z) / 3) * 3 * flat;
        int cx = AuthoredForge.districtCenter(x), cz = AuthoredForge.districtCenter(z);
        int shaftX = cx - 39;
        Chamber landing = chamber(seed, (int)Math.round(shaftX / 64.0), (int)Math.round(cz / 64.0), chambers);
        clearance = Math.max(clearance, 16 - Math.hypot(x - shaftX, z - cz));
        clearance = Math.max(clearance, 6 - passage(x, z, shaftX, cz, landing.x, landing.z));
        double chamberSpace = Math.clamp((closest.radius - nearest) / 9, 0, 1);
        double height = 5.5 + noise(seed ^ 6197, x / 58.0, z / 58.0) * 6
                + chamberSpace * (13 + noise(seed ^ 379, x / 83.0, z / 83.0) * 22);
        height = Math.min(height, LabyrinthLevels.CAVE_ROOF_Y - 1 - floor);
        double round = Math.sqrt(Math.clamp(clearance / 7, 0, 1));
        return new Column(floor + height * (1 - round) * .5, floor + height * (1 + round) * .5, clearance);
    }

    public static int floorY(long seed, int x, int z) { return (int)Math.floor(column(seed, x, z).floor); }

    public static void generate(ChunkAccess chunk, long seed) {
        if (chunk.getMinY() > LabyrinthLevels.CAVE_BOTTOM_Y) return;
        var pos = new BlockPos.MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
         
         
        var chambers = new java.util.HashMap<Long, Chamber>();
        Column[][] columns = new Column[18][18];
        for (int dx = 0; dx < 18; dx++) for (int dz = 0; dz < 18; dz++)
            columns[dx][dz] = column(seed, minX + dx - 1, minZ + dz - 1, chambers);
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++)
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                int dx = x - minX + 1, dz = z - minZ + 1;
                Column cave = columns[dx][dz];
                int floor = (int)Math.floor(cave.floor), roof = (int)Math.ceil(cave.roof);
                boolean open = cave.clearance > .3 && roof - floor >= 4;
                boolean flooded = open && cave.clearance > 4 && floodRegion(seed, x, z);
                int waterLine = Math.min(roof - 2, flooded ? floodLevel(seed, x, z, floor) : floor);
                boolean puddle = !flooded && open && cave.clearance > 5 && wet(seed, x, z)
                        && columns[dx - 1][dz].floor >= floor && columns[dx + 1][dz].floor >= floor
                        && columns[dx][dz - 1].floor >= floor && columns[dx][dz + 1].floor >= floor;
                for (int y = LabyrinthLevels.CAVE_BOTTOM_Y; y <= LabyrinthLevels.CAVE_ROOF_Y; y++) {
                    BlockState state;
                    if (y <= LabyrinthLevels.CAVE_BOTTOM_Y + 2) state = base(shaded(seed, x, y, z)).defaultBlockState();
                    else if (open && y > floor && y < roof)
                        state = y <= waterLine ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    else {
                        state = rock(seed, x, y, z);
                        if (open && y == floor) {
                            if (puddle) state = Blocks.WATER.defaultBlockState();
                            else if (!CatacombProtection.isOre(state)) state = smoothFloor(seed, x, z, columns, dx, dz, state);
                        } else if (open && y == roof && !CatacombProtection.isOre(state)
                                && cave.roof - Math.floor(cave.roof) < .5)
                            state = slab(shaded(seed, x, y, z)).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
                    }
                    chunk.setBlockState(pos.set(x, y, z), state, 0);
                }
                if (open && !puddle && !flooded && cave.clearance > 4
                        && noise(seed ^ 7119, x / 31.0, z / 31.0) > .56) {
                    if (!CatacombProtection.isOre(chunk.getBlockState(pos.set(x, floor, z)))
                            && chunk.getBlockState(pos).isCollisionShapeFullBlock(chunk, pos)) {
                        chunk.setBlockState(pos, Asterion.ANCIENT_MOSS.defaultBlockState(), 0);
                        long plant = CatacombLayout.hash(seed ^ 727, x, z);
                        if (Math.floorMod(plant, 9) == 0)
                            chunk.setBlockState(pos.set(x, floor + 1, z), Asterion.ANCIENT_MOSS_CARPET.defaultBlockState(), 0);
                        else if (Math.floorMod(plant, 23) == 0)
                            chunk.setBlockState(pos.set(x, floor + 1, z), Blocks.BROWN_MUSHROOM.defaultBlockState(), 0);
                    }
                }
                if (open && !puddle && !flooded) spikes(chunk, seed, x, z, floor, roof, cave.clearance);
                if (flooded) underwaterVines(chunk, seed, x, z, floor, waterLine, cave.clearance);
                if (open && roof + 2 <= LabyrinthLevels.CAVE_ROOF_Y && wet(seed ^ 0xD21FL, x, z)
                        && Math.floorMod(CatacombLayout.hash(seed, x, z), 5) == 0) {
                     
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
            if (!state.isAir()) return;
        }
        generate(chunk, seed);
        chunk.markUnsaved();
    }

    private static BlockPos marker(ChunkAccess chunk) {
        return new BlockPos(chunk.getPos().getMinBlockX(), LabyrinthLevels.CAVE_BOTTOM_Y + 1, chunk.getPos().getMinBlockZ());
    }
    private static BlockState revision() { return Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 2); }

    private static boolean wet(long seed, int x, int z) {
        return noise(seed ^ 751, x / 23.0, z / 23.0) > .64
                && noise(seed ^ 929, x / 7.0, z / 7.0) > .4;
    }

     
    private static boolean floodRegion(long seed, int x, int z) {
        double basin = noise(seed ^ 0xF100D5L, x / 118.0, z / 118.0);
        double shore = noise(seed ^ 0xA911L, x / 29.0, z / 29.0);
        return basin * .78 + shore * .22 > .69;
    }

    private static int floodLevel(long seed, int x, int z, int floor) {
        int depth = 3 + (int)Math.floor(noise(seed ^ 0xB451L, x / 51.0, z / 51.0) * 9);
        return floor + depth;
    }

    private static void underwaterVines(ChunkAccess chunk, long seed, int x, int z,
                                        int floor, int waterLine, double clearance) {
        if (clearance < 7 || waterLine - floor < 3
                || Math.floorMod(CatacombLayout.hash(seed ^ 0x11A7L, x, z), 173) != 0) return;
        int length = Math.min(waterLine - floor, 2 + Math.floorMod(
                CatacombLayout.hash(seed ^ 0x61A0L, x, z), 4));
        for (int offset = 1; offset <= length; offset++) {
            chunk.setBlockState(new BlockPos(x, floor + offset, z), Asterion.LABYRINTH_VINE.defaultBlockState()
                    .setValue(net.krodark.asterion.block.LabyrinthVineBlock.FACING, Direction.UP)
                    .setValue(net.krodark.asterion.block.LabyrinthVineBlock.END, offset == length)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true), 0);
        }
    }

    static boolean shaded(long seed, int x, int y, int z) {
        double depth = Math.clamp((13.0 - y) / 77.0, 0.0, 1.0);
        double patches = noise(seed ^ 0x5A1EL, x / 5.0 + y / 9.0, z / 5.0 - y / 11.0);
        double grain = unit(seed ^ (long)y * 0x51EDL, x, z);
        return patches * .55 + grain * .45 < .29 + depth * .42;
    }

    private static BlockState rock(long seed, int x, int y, int z) {
        boolean dark = shaded(seed, x, y, z);
        int vx = Math.floorDiv(x, 7), vy = Math.floorDiv(y, 7), vz = Math.floorDiv(z, 7);
        long vein = CatacombLayout.hash(seed ^ vy * 0x51EDL, vx, vz);
        double ox = Math.floorMod(x, 7) - (2 + Math.floorMod(vein >>> 8, 3));
        double oy = Math.floorMod(y, 7) - (2 + Math.floorMod(vein >>> 16, 3));
        double oz = Math.floorMod(z, 7) - (2 + Math.floorMod(vein >>> 24, 3));
        double radius = 1.35 + Math.floorMod(vein >>> 32, 8) * .1;
        if (Math.floorMod(vein, 7) == 0 && ox * ox + oy * oy * 1.8 + oz * oz < radius * radius) {
            boolean celestial = Math.floorMod(vein >>> 40, 14) == 0;
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
        if (best - height < .15) return fraction < .55 ? slab(dark).defaultBlockState() : fallback;
        return stairs(dark).defaultBlockState().setValue(StairBlock.FACING, uphill);
    }

    private static void spikes(ChunkAccess chunk, long seed, int x, int z, int floor, int roof, double clearance) {
        long roll = CatacombLayout.hash(seed ^ 0x51A6EL, Math.floorDiv(x, 18), Math.floorDiv(z, 18));
        if (Math.floorMod(roll, 3) != 0 || clearance < 9 || roof - floor < 10) return;
        int dx = Math.floorMod(x, 18) - (3 + (int)Math.floorMod(roll >>> 8, 12));
        int dz = Math.floorMod(z, 18) - (3 + (int)Math.floorMod(roll >>> 16, 12));
        if (dx != 0 || dz != 0) return;
        int height = 4 + (int)Math.floorMod(roll >>> 24, 6);
        height = Math.min(height, roof - floor - 4);
        if (height <= 0) return;
        boolean hanging = (roll & 1) != 0;
        for (int offset = 1; offset <= height; offset++) {
            int y = hanging ? roof - offset : floor + offset;
            boolean dark = shaded(seed, x, y, z);
            BlockState state = base(dark).defaultBlockState();
            int remaining = height - offset + 1;
            if (remaining <= 8) state = (dark ? Asterion.SHADED_SHALE_FORMATION : Asterion.SHALE_FORMATION)
                    .defaultBlockState()
                    .setValue(net.krodark.asterion.block.ShaleFormationBlock.THICKNESS, (remaining + 1) / 2)
                    .setValue(net.krodark.asterion.block.ShaleFormationBlock.HANGING, hanging);
            chunk.setBlockState(new BlockPos(x, y, z), state, 0);
        }
    }

    private static Block base(boolean shaded) { return shaded ? Asterion.SHADED_SHALE : Asterion.SHALE; }
    private static Block slab(boolean shaded) { return shaded ? Asterion.SHADED_SHALE_SLAB : Asterion.SHALE_SLAB; }
    private static Block stairs(boolean shaded) { return shaded ? Asterion.SHADED_SHALE_STAIRS : Asterion.SHALE_STAIRS; }
}
