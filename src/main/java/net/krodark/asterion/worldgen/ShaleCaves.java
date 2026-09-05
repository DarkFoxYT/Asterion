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

    private static Column column(long seed, int x, int z) {
        double phase = phase(seed);
        double wx = x + Math.sin(z * .018 + phase) * 8;
        double wz = z + Math.sin(x * .017 - phase) * 8;
        double dx = Math.abs(wx - Math.rint(wx / 64) * 64);
        double dz = Math.abs(wz - Math.rint(wz / 64) * 64);
        double radius = 20 + 4 * Math.sin(x * .009 + z * .011 + phase);
        double room = Math.hypot(dx, dz * 1.12) - radius;
        double passage = Math.min(dx, dz) - (5 + Math.sin((x + z) * .025 + phase));
        double overlap = Math.max(0, 6 - Math.abs(room - passage)) / 6;
        double clearance = -(Math.min(room, passage) - overlap * overlap * 1.5);
        // Each mine ladder opens into a widened section of the nearest north/south passage.
        int cx = AuthoredForge.districtCenter(x), cz = AuthoredForge.districtCenter(z);
        int shaftX = Math.floorDiv(cx - 24, 64) * 64;
        clearance = Math.max(clearance, 13 - Math.hypot(x - shaftX, z - cz));
        double floor = -33 + 11 * Math.sin(x * .012 + phase) + 8 * Math.cos(z * .014 - phase);
        double height = 13 + 4 * Math.sin(x * .015 - z * .019 + phase);
        double round = Math.sqrt(Math.clamp(clearance / 7, 0, 1));
        return new Column(floor + height * (1 - round) * .5, floor + height * (1 + round) * .5, clearance);
    }

    public static int floorY(long seed, int x, int z) { return (int)Math.floor(column(seed, x, z).floor); }

    public static void generate(ChunkAccess chunk, long seed) {
        if (chunk.getMinY() > LabyrinthLevels.CAVE_BOTTOM_Y) return;
        var pos = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++)
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                Column cave = column(seed, x, z);
                int floor = (int)Math.floor(cave.floor), roof = (int)Math.ceil(cave.roof);
                boolean open = cave.clearance > .3 && roof - floor >= 4;
                boolean puddle = open && cave.clearance > 5 && wet(seed, x, z)
                        && floorY(seed, x - 1, z) >= floor && floorY(seed, x + 1, z) >= floor
                        && floorY(seed, x, z - 1) >= floor && floorY(seed, x, z + 1) >= floor;
                for (int y = LabyrinthLevels.CAVE_BOTTOM_Y; y <= LabyrinthLevels.CAVE_ROOF_Y; y++) {
                    BlockState state;
                    if (y <= LabyrinthLevels.CAVE_BOTTOM_Y + 2) state = Blocks.BEDROCK.defaultBlockState();
                    else if (open && y > floor && y < roof) state = Blocks.AIR.defaultBlockState();
                    else {
                        state = rock(seed, x, y, z);
                        if (open && y == floor) {
                            if (puddle) state = Blocks.WATER.defaultBlockState();
                            else if (!CatacombProtection.isOre(state)) state = smoothFloor(seed, x, z, cave.floor, state);
                        } else if (open && y == roof && !CatacombProtection.isOre(state)
                                && cave.roof - Math.floor(cave.roof) < .5)
                            state = slab(shaded(seed, x, y, z)).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
                    }
                    // Keep the old terrain-generation sentinel, buried in the roof rock.
                    if (y == 1 && x == chunk.getPos().getMinBlockX() && z == chunk.getPos().getMinBlockZ())
                        state = Blocks.BEDROCK.defaultBlockState();
                    chunk.setBlockState(pos.set(x, y, z), state, 0);
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
        double dx = x - Math.rint(x / 64.0) * 64, dz = z - Math.rint(z / 64.0) * 64;
        return dx * dx + dz * dz < 20
                || Math.sin(x * .14 + phase(seed)) + Math.cos(z * .12 - phase(seed)) > 1.25;
    }

    private static boolean shaded(long seed, int x, int y, int z) {
        double band = 7 * Math.sin(x * .055 + phase(seed)) + 5 * Math.cos(z * .06 - phase(seed));
        return y < -24 + band;
    }

    private static BlockState rock(long seed, int x, int y, int z) {
        boolean dark = shaded(seed, x, y, z);
        long vein = CatacombLayout.hash(seed ^ Math.floorDiv(y, 4) * 0x51EDL, Math.floorDiv(x, 4), Math.floorDiv(z, 4));
        if (Math.floorMod(vein, 17) == 0 && Math.floorMod(x, 4) != 3 && Math.floorMod(z, 4) != 3 && Math.floorMod(y, 4) != 3) {
            Block ore = switch ((int)Math.floorMod(vein >>> 8, 10)) {
                case 0, 1, 2, 3 -> Asterion.CELESTIAL_BRONZE_ORE;
                case 4 -> Asterion.TARNISHED_GOLD_ORE;
                case 5 -> Asterion.CELESTIAL_GOLD_ORE;
                case 6, 7 -> dark ? Asterion.SHADED_SHALE_TARNISHED_GOLD_ORE : Asterion.SHALE_TARNISHED_GOLD_ORE;
                default -> dark ? Asterion.SHADED_SHALE_CELESTIAL_GOLD_ORE : Asterion.SHALE_CELESTIAL_GOLD_ORE;
            };
            return ore.defaultBlockState();
        }
        return base(dark).defaultBlockState();
    }

    private static BlockState smoothFloor(long seed, int x, int z, double height, BlockState fallback) {
        boolean dark = shaded(seed, x, (int)height, z);
        double fraction = height - Math.floor(height);
        if (fraction < .3) return slab(dark).defaultBlockState();
        if (fraction > .8) return fallback;
        Direction uphill = Direction.NORTH;
        double best = height;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            double neighbor = column(seed, x + direction.getStepX(), z + direction.getStepZ()).floor;
            if (neighbor > best) { best = neighbor; uphill = direction; }
        }
        return stairs(dark).defaultBlockState().setValue(StairBlock.FACING, uphill);
    }

    private static void spikes(ChunkAccess chunk, long seed, int x, int z, int floor, int roof, double clearance) {
        long roll = CatacombLayout.hash(seed ^ 0x51A6EL, Math.floorDiv(x, 11), Math.floorDiv(z, 11));
        if (Math.floorMod(roll, 3) != 0 || clearance < 8 || roof - floor < 9) return;
        double distance = Math.hypot(Math.floorMod(x, 11) - 5, Math.floorMod(z, 11) - 5);
        int height = (int)Math.floor(4.8 - distance * 2);
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
