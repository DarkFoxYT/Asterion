package net.krodark.asterion.update.underworld.world;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.krodark.asterion.block.ShaleFormationBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** A shared river tunnel opens onto the shore of an unbounded subterranean sea. */
public final class UnderworldTerrain {
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 159;
    public static final int WATER_Y = 47;
    public static final int START_Z = -840;
    public static final int END_Z = 1024;
    public static final int SPAWN_Z = -780;
    public static final int SPAWN_X = (int)Math.floor(riverCenter(SPAWN_Z) - 15);
    public static final int SPAWN_Y = WATER_Y + 3;
    public static final int FERRY_Z = 58;
    private static final int BRANCH_SPACING = 80;

    /** Branches use a fixed topology salt so teleport and web placement match chunk generation. */
    private static Branch branch(int z) {
        int slot = Math.floorDiv(z - SPAWN_Z, BRANCH_SPACING);
        int centerZ = SPAWN_Z + slot * BRANCH_SPACING + BRANCH_SPACING / 2;
        long shape = hash(0x51DECA7EL, slot, 0);
        int side = (shape & 1L) == 0 ? -1 : 1;
        int reach = 28 + (int)((shape >>> 4) & 15);
        return new Branch(slot, centerZ, side, reach);
    }
    private record Branch(int slot, int centerZ, int side, int reach) { }
    public static BlockPos chamberCenter(int slot) {
        Branch b = branch(SPAWN_Z + slot * BRANCH_SPACING + BRANCH_SPACING / 2);
        int z = b.centerZ + 21;
        int x = (int)Math.round(riverCenter(z) - 15 + b.side * b.reach);
        return new BlockPos(x, pathFloor(z) + 1, z);
    }
    public static BlockPos randomSpawn(java.util.UUID player) {
        int slot = Math.floorMod(player.hashCode(), 4);
        return chamberCenter(slot);
    }
    public static boolean inChamber(BlockPos pos) {
        Branch b = branch(pos.getZ());
        BlockPos center = chamberCenter(b.slot);
        double dx = (pos.getX() - center.getX()) / 9.0, dz = (pos.getZ() - center.getZ()) / 10.0;
        return dx * dx + dz * dz < 1.0;
    }
    public static double chamberWebX(int z) {
        Branch b = branch(z);
        return Math.abs(z - (b.centerZ + 21)) <= 9 ? chamberCenter(b.slot).getX() : Double.NaN;
    }

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
        java.util.Map<Long, CaveNode> caveNodes = new java.util.HashMap<>();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                Column c = column(seed, x, z, caveNodes);
                boolean shaded = octaves(seed ^ 0x5ADE, x * .019, z * .019) > .08;
                BlockState stone = (shaded ? Asterion.DEAD_STONE_2 : Asterion.DEAD_STONE).defaultBlockState();
                BlockState shale = (shaded ? Asterion.SHADED_SHALE : Asterion.SHALE).defaultBlockState();
                Details d = details(seed, x, z, c);
                double pool = puddleShape(seed, x, z);
                boolean spiderPuddle = c.spider && !c.path && (z >= 18 || Math.abs(x - riverCenter(z)) > 4)
                        && spiderPuddle(seed, x, z, c.floor + 1);
                int poolY = puddleWaterY(seed, z);
                int torch = pillarHeight(seed, x, z, c);
                double offset = x - riverCenter(z);
                double pathDistance = Math.abs(x - (z >= 12 ? landingX(z) : riverCenter(z) - 15));
                int pathPalette = pathMaterial(seed, x, z);
                BlockState pathSurface = switch (pathPalette) {
                    case 0 -> Asterion.DEAD_STONE.defaultBlockState();
                    case 1 -> Asterion.DEAD_STONE_2.defaultBlockState();
                    case 2 -> Asterion.SHALE.defaultBlockState();
                    default -> Asterion.SHADED_SHALE.defaultBlockState();
                };
                boolean paving = c.path && pathDistance <= 2;
                int nextFloor = paving ? pathFloor(z + 1) : c.floor;
                int previousFloor = paving ? pathFloor(z - 1) : c.floor;
                boolean dock = dockColumn(x, z);
                boolean post = dock && (z == FERRY_Z - 6 || z == FERRY_Z + 4)
                        && (x == dockRight() - 6 || x == dockRight());
                boolean joined = joinedPillar(seed, x, z, c);
                boolean sideHall = sideHall(x, z);
                boolean chamber = inChamber(new BlockPos(x, c.floor + 1, z));
                long texture = hash(seed, x, z);
                net.minecraft.core.Direction shoulder = d.rock > 1 && d.spike == 0 && !joined && (texture & 3) == 1
                        ? spireFacing(seed ^ 0xB16, x, z) : net.minecraft.core.Direction.NORTH;
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    BlockState state;
                    if (y == MIN_Y || y == MAX_Y) state = Blocks.BEDROCK.defaultBlockState();
                    else if (!c.open || y <= c.floor || y >= c.roof) state = stone;
                    else if (z >= 18 && y <= WATER_Y) state = Blocks.WATER.defaultBlockState();
                    else state = Blocks.AIR.defaultBlockState();
                    if (c.open && y > MIN_Y && y < MAX_Y) {
                        if (c.spider && y == c.roof && c.roof - c.floor > 5 && (texture & 3) == 0)
                            state = (shaded ? Asterion.SHADED_SHALE_SLAB : Asterion.SHALE_SLAB)
                                    .defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
                        if (y == c.floor && !c.path) state = d.mud || spiderPuddle ? Blocks.MUD.defaultBlockState() : shale;
                        if (spiderPuddle && y == c.floor + 1) state = Blocks.WATER.defaultBlockState();
                        if (y > c.floor && y < c.roof && !c.path && pool > 1.3 && torch == 0) {
                            if (joined || y <= c.floor + d.rock || y >= c.roof - d.hanging)
                                state = y % 5 == 0 ? stone : shale;
                            else if (y <= c.floor + d.rock + d.spike)
                                state = formation(false, c.floor + d.rock + d.spike - y, d.spike, y <= WATER_Y && z >= 18);
                            if (!joined && d.rock > 1 && d.spike == 0 && y == c.floor + d.rock) {
                                if ((texture & 3) == 0) state = (shaded ? Asterion.DEAD_STONE_2_SLAB : Asterion.DEAD_STONE_SLAB).defaultBlockState();
                                else if ((texture & 3) == 1) state = (shaded ? Asterion.DEAD_STONE_2_STAIRS : Asterion.DEAD_STONE_STAIRS).defaultBlockState()
                                        .setValue(BlockStateProperties.HORIZONTAL_FACING, shoulder);
                            }
                        }
                        // Paving owns the entire clear route, with stair transitions between terraces.
                        if (c.path && y == c.floor) state = pathSurface;
                        if (c.spider && !spiderPuddle && !c.path && y == c.floor) state = (texture & 3) == 0
                                ? Asterion.DEAD_STONE_2.defaultBlockState() : pathSurface;
                        if (sideHall && !chamber && !spiderPuddle && !c.path && y == c.floor + 1 && (texture & 31) == 3)
                            state = Asterion.DEAD_STONE_SLAB.defaultBlockState();
                        if (sideHall && !chamber && !spiderPuddle && !c.path && y == c.floor + 1 && (texture & 63) == 7)
                            state = Asterion.DEAD_STONE_WALL.defaultBlockState();
                        if (sideHall && !chamber && !spiderPuddle && !c.path && y == c.floor + 1
                                && pathFloor(z + 1) > c.floor)
                            state = Asterion.DEAD_STONE_STAIRS.defaultBlockState()
                                    .setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH);
                        if (paving && !dock && y == c.floor + 1) {
                            if (nextFloor > c.floor || previousFloor > c.floor)
                                state = Asterion.DEAD_STONE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                                        nextFloor > c.floor ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH);
                        }
                        // Isolated basins have their own surface elevation; the ocean never fills them.
                        if (pool <= 1.3 && Math.abs(offset) > 4 && !c.path && !c.spider) {
                            if (y >= poolY - 2 && y <= poolY - 1) state = Blocks.MUD.defaultBlockState();
                            if (pool <= 1.03 && y == poolY)
                                state = Asterion.DEAD_STONE_SLAB.defaultBlockState()
                                        .setValue(BlockStateProperties.WATERLOGGED, true);
                            if (pool > 1.03 && y == poolY) state = Blocks.MUD.defaultBlockState();
                            if (pool > 1.03 && y > poolY && y <= c.floor) state = Blocks.AIR.defaultBlockState();
                        }
                        if (torch > 0 && y > c.floor && y <= c.floor + torch)
                            state = Asterion.GREEK_FIRE_FLOOR_TORCH.defaultBlockState()
                                    .setValue(net.krodark.asterion.block.GreekFireTorchBlock.TOP, y == c.floor + torch)
                                    .setValue(net.krodark.asterion.block.GreekFireTorchBlock.LIT, true);
                        // Compact seven-by-eleven platform over water, with four corner piles.
                        // Leave the shore entrance and ferry-facing side open for boarding.
                        if (dock) {
                            if (y == WATER_Y + 1) state = Asterion.DEAD_WOOD_PLANKS.defaultBlockState();
                            if (post && y >= c.floor && y <= WATER_Y + 2) state = Asterion.DEAD_WOOD.defaultBlockState();
                            if (!post && y == WATER_Y + 2 && x == dockRight() - 6)
                                state = Asterion.DEAD_WOOD_FENCE.defaultBlockState()
                                        .setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
                        }
                    }
                    if (!state.isAir()) {
                        chunk.setBlockState(pos.set(x, y, z), state, 0);
                        // These torches render entirely through their block entity, including each shaft.
                        if (state.is(Asterion.GREEK_FIRE_FLOOR_TORCH))
                            chunk.setBlockEntity(new net.krodark.asterion.block.GreekFireTorchBlockEntity(pos.immutable(), state));
                    }
                }
            }
        }
    }

    /** Deadstone core, broken mixed shoulders, then shale; stable across chunk boundaries. */
    private static int pathMaterial(long seed, int x, int z) {
        double route = z >= 12 ? landingX(z) : riverCenter(z) - 15;
        double distance = Math.abs(x - route - Math.sin(z * .075) * .65);
        double edge = 1.7 + octaves(seed ^ 0xA6E, x * .18, z * .18) * 1.2;
        double fleck = (hash(seed ^ 0xBADC, x, z) >>> 11) * 0x1.0p-53;
        boolean deadstone = distance < 1 || distance < edge + .9 && fleck > smooth((distance - edge) / .9);
        boolean variant = octaves(seed ^ 0x5ADE, x * .14, z * .14) + (fleck - .5) * .35 > 0;
        return (deadstone ? 0 : 2) + (variant ? 1 : 0);
    }

    /** Broad, discrete terraces replace column noise on the walking floor. */
    private static int pathFloor(int z) {
        return WATER_Y + 1 + (int)Math.floor(2.5 * smooth((z + 104.0) / 20)
                * (1 - smooth((z + 56.0) / 28)));
    }

    private static int dockRight() {
        // Keep the straight boarding edge outside the ferry hull throughout the berth.
        return (int)Math.floor(Math.min(riverCenter(FERRY_Z - 6),
                Math.min(riverCenter(FERRY_Z), riverCenter(FERRY_Z + 4)))) - 3;
    }

    private static double landingX(int z) {
        double t = smooth((z - 12.0) / (FERRY_Z - 6 - 12.0));
        return (riverCenter(z) - 15) * (1 - t) + (dockRight() - 3) * t;
    }

    private static boolean dockColumn(int x, int z) {
        return z >= FERRY_Z - 6 && z <= FERRY_Z + 4 && x >= dockRight() - 6 && x <= dockRight();
    }

    private static Column column(long seed, int x, int z, java.util.Map<Long, CaveNode> caveNodes) {
        double center = riverCenter(z), offset = x - center;
        double mouth = smooth((z + 12.0) / 56);
        // Shift the dry cave toward the player bank instead of wasting width beyond the path.
        double lateral = Math.abs(offset + 5 * (1 - mouth));
        double cap = Math.sqrt(Math.max(0, 1 - Math.pow(Math.clamp((START_Z + 30.0 - z) / 30, 0, 1), 2)));
        double width = (tunnelWidth(seed, z) + mouth * 24) * cap;
        boolean tunnel = z > START_Z && lateral < width;
        double coast = 30 + 9 * octaves(seed ^ 0xC0457, x * .009, 0);
        double seaDistance = z - coast;
        boolean sea = z >= 18 && seaDistance > -24;
        boolean open = tunnel || sea;
        double routeX = z >= 12 ? landingX(z) : center - 15;
        boolean path = tunnel && Math.abs(x - routeX) <= 4 && z < FERRY_Z - 6
                && (z < 18 || x + 1 <= center - 1.25);
        SideShape side = sideShape(x, z);
        SideShape spider = spiderCave(seed, x, z, caveNodes);
        double floor = pathFloor(z) + Math.floor(smooth((Math.abs(offset + 15) - 4) / 10) * 3);
        double arch = Math.sqrt(Math.max(0, 1 - Math.pow(lateral / Math.max(1, width), 2)));
        double roof = floor + 5 + arch * (15 + 8 * (1 - tunnelConstriction(seed, z))) * cap;
        if (side.open && z < -36) {
            open = true;
            floor = Math.min(floor, side.floor);
            roof = Math.max(roof, side.roof);
        }
        if (spider.open) {
            open = true;
            floor = Math.min(floor, spider.floor);
            roof = Math.max(roof, spider.roof);
        }
        int ravine = ravineDepth(seed, x, z);
        if (ravine > 0 && !side.open && !spider.open && !path) {
            open = true;
            floor -= ravine;
            roof += 3;
        }
        if (z >= 18) {
            double channel = 8.5 + 1.5 * octaves(seed ^ 0x71AE, z * .018, 0);
            double dryFloor = floor;
            floor = WATER_Y - 9 + smooth((Math.abs(offset) - channel) / 8) * 13;
            if (sea) {
                double depth = smooth((seaDistance + 3) / 90);
                double seaFloor = WATER_Y + 4 - depth * 34 + octaves(seed ^ 0x5EA, x * .012, z * .012) * 2 * depth;
                floor = tunnel ? Math.min(floor, seaFloor) : seaFloor;
                roof += (133 + 12 * octaves(seed ^ 0xA2C4, x * .008, z * .008) - roof) * smooth((seaDistance + 24) / 55);
            }
            if (tunnel && Math.abs(offset) < channel) floor = Math.min(floor, WATER_Y - 7);
            // Ease into the submerged channel instead of cutting a trench at z=18.
            if (tunnel) floor = dryFloor + (floor - dryFloor) * smooth((z - 18.0) / 30);
        }
        if (z >= 12 && z <= FERRY_Z + 4 && Math.abs(x - landingX(z)) <= 1.5 && x + 1 <= center - 1.25) {
            open = true; path = true;
        }
        // A broad, sloping mainland apron reaches the dock entrance, rather than a raised causeway.
        if (z >= 12 && z < 112 && offset < -3) {
            double bankDistance = Math.abs(x - landingX(z));
            if (bankDistance < 20) {
                // Continue the bank below the dock; no rectangular end at its front edge.
                double shelf = pathFloor(z) - Math.max(0, bankDistance - 2) * .8
                        - Math.max(0, z - 46) * .7;
                double blend = 1 - smooth((bankDistance - 6) / 14);
                floor += Math.max(0, shelf - floor) * blend;
                open = true;
                roof = Math.max(roof, floor + 10);
            }
        }
        double seabed = floor;
        if (path) { floor = pathFloor(z); roof = Math.max(roof, floor + 10); }
        if (tunnel && Math.abs(offset) > 4 && !path && !side.open && !spider.open
                && puddleShape(seed, x, z) <= 1.03) floor = puddleWaterY(seed, z) - 1;
        if (dockColumn(x, z)) { open = true; path = true; floor = seabed; roof = Math.max(roof, WATER_Y + 12); }
        // Side chambers may cross the central tunnel, but their wet floor must not
        // cut a submerged trench through the dry route before the river mouth.
        if (z < 18 && tunnel && Math.abs(offset) <= 4) floor = Math.max(floor, WATER_Y + 1);
        int floorBlock = (int)Math.floor(floor);
        boolean spiderSurface = side.open || spider.open;
        if (spiderSurface && !path && (z >= 18 || Math.abs(offset) > 4)
                && spiderPuddle(seed, x, z, floorBlock)) floorBlock--;
        return new Column(open, floorBlock, (int)Math.ceil(Math.min(MAX_Y - 1, roof)), path, spiderSurface);
    }

    private record SideShape(boolean open, int floor, int roof) { }
    private record CaveNode(double x, double z, double radius, double stretch) { }
    private static CaveNode caveNode(long seed, int cx, int cz, java.util.Map<Long, CaveNode> cache) {
        long key = net.minecraft.world.level.ChunkPos.pack(cx, cz);
        return cache.computeIfAbsent(key, ignored -> {
            long shape = hash(seed ^ 0x5A1EC4A7EL, cx, cz);
            double jitterX = ((shape >>> 11) * 0x1.0p-53 - .5) * 13;
            double jitterZ = ((hash(shape, cx, cz) >>> 11) * 0x1.0p-53 - .5) * 13;
            return new CaveNode(cx * 48 + 24 + jitterX, cz * 48 + 24 + jitterZ,
                    11 + ((shape >>> 32) & 7), .8 + ((shape >>> 40) & 7) * .055);
        });
    }
    private static double segmentDistance(double x, double z, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double t = Math.clamp(((x - ax) * dx + (z - az) * dz) / Math.max(1, dx * dx + dz * dz), 0, 1);
        return Math.hypot(x - ax - dx * t, z - az - dz * t);
    }
    private static SideShape spiderCave(long seed, int x, int z, java.util.Map<Long, CaveNode> nodes) {
        // ShaleCaves-style rounded nodes and linked corridors, repeated without an X/Z cap.
        // The dry side of Limbo continues indefinitely; keep the ferry and river untouched.
        if (z >= -42) return new SideShape(false, 0, 0);
        double u = x - (riverCenter(z) - 15);
        if (Math.abs(u) < 21) return new SideShape(false, 0, 0);
        int cx = (int)Math.floor(u / 48), cz = Math.floorDiv(z, 48);
        double clearance = -100;
        for (int ix = cx - 1; ix <= cx + 1; ix++) for (int iz = cz - 1; iz <= cz + 1; iz++) {
            CaveNode node = caveNode(seed, ix, iz, nodes);
            clearance = Math.max(clearance, node.radius - Math.hypot(u - node.x, (z - node.z) * node.stretch));
            CaveNode east = caveNode(seed, ix + 1, iz, nodes);
            CaveNode south = caveNode(seed, ix, iz + 1, nodes);
            double width = 2.7 + ((hash(seed ^ 0x51DE, ix, iz) >>> 8) & 3) * .48;
            clearance = Math.max(clearance, width - segmentDistance(u, z, node.x, node.z, east.x, east.z));
            clearance = Math.max(clearance, width - segmentDistance(u, z, node.x, node.z, south.x, south.z));
        }
        // Every authored spider chamber has a short, guaranteed join to the grid.
        int nearbySlot = branch(z).slot;
        for (int slot = nearbySlot - 1; slot <= nearbySlot + 1; slot++) {
            BlockPos entrance = chamberCenter(slot);
            if (Math.abs(z - entrance.getZ()) >= 46) continue;
            Branch branch = branch(entrance.getZ());
            CaveNode nearest = caveNode(seed, branch.side > 0 ? 0 : -1, Math.floorDiv(entrance.getZ(), 48), nodes);
            double entranceU = entrance.getX() - (riverCenter(entrance.getZ()) - 15);
            clearance = Math.max(clearance, 14 - Math.hypot((u - entranceU) * .95,
                    (z - entrance.getZ()) * .8));
            clearance = Math.max(clearance, 3.5 - segmentDistance(u, z, entranceU,
                    entrance.getZ(), nearest.x, nearest.z));
        }
        if (clearance <= .35) return new SideShape(false, 0, 0);
        double round = Math.sqrt(Math.clamp(clearance / 8, 0, 1));
        double height = 7 + Math.min(25, Math.max(0, clearance) * 1.8);
        double base = pathFloor(z) - 1 + 1.8 * octaves(seed ^ 0x6A0DL, u * .025, z * .025);
        int floor = (int)Math.floor(base + height * (1 - round) * .24);
        int roof = (int)Math.ceil(base + height * (1 + round) * .76);
        return new SideShape(roof - floor >= 4, floor, roof);
    }
    private static boolean spiderPuddle(long seed, int x, int z, int floor) {
        if (floor < WATER_Y - 2) return false;
        double broad = octaves(seed ^ 0xBADD1EL, x * .075, z * .075);
        double fine = octaves(seed ^ 0x71DEL, x * .21, z * .21);
        return broad > .28 && fine > .04 && (hash(seed ^ 0xF100DL, x >> 3, z >> 3) & 3) != 0;
    }
    private static int ravineDepth(long seed, int x, int z) {
        if (z < SPAWN_Z + 48 || z > -90) return 0;
        int slot = Math.floorDiv(z - SPAWN_Z, 232);
        long shape = hash(seed ^ 0x4A7E11EL, slot, 0);
        if ((shape & 3L) != 0) return 0; // Most regions have no ravine at all.
        int centerZ = SPAWN_Z + slot * 232 + 92 + (int)((shape >>> 7) & 63);
        double halfLength = 16 + ((shape >>> 14) & 15);
        double along = (z - centerZ) / halfLength;
        if (Math.abs(along) >= 1) return 0;
        int side = (shape & 4L) == 0 ? -1 : 1;
        double meander = octaves(seed ^ 0x4A71L, z * .033, slot * .71) * 4.5
                + Math.sin(z * .087 + (shape & 255)) * 1.6;
        double route = riverCenter(z) - 15;
        double centerX = route + side * (12 + ((shape >>> 20) & 5)) + meander;
        double width = 2.2 + ((shape >>> 26) & 3) * .45
                + octaves(seed ^ 0x8A91L, z * .068, slot) * .7;
        double across = Math.abs(x - centerX) / Math.max(1.6, width);
        if (across >= 1) return 0;
        double taper = (1 - smooth(Math.abs(along))) * (1 - smooth(across));
        double rough = .75 + .25 * octaves(seed ^ 0xA71EL, x * .16, z * .12);
        return Math.max(0, (int)Math.round(taper * rough * (7 + ((shape >>> 30) & 5))));
    }
    private static boolean sideHall(int x, int z) { return sideShape(x, z).open; }
    private static SideShape sideShape(int x, int z) {
        if (z < SPAWN_Z - 12 || z > -42) return new SideShape(false, 0, 0);
        Branch b = branch(z);
        double progress = (z - (b.centerZ - 23.0)) / 44.0;
        double route = riverCenter(z) - 15;
        double passageX = route + b.side * (3 + b.reach * smooth(progress));
        double width = 2.7 + .8 * Math.sin(z * .14 + b.slot);
        boolean hall = progress >= 0 && progress <= 1 && Math.abs(x - passageX) < width;
        BlockPos chamber = chamberCenter(b.slot);
        double dx = (x - chamber.getX()) / 9.0, dz = (z - chamber.getZ()) / 10.0;
        boolean room = dx * dx + dz * dz < 1.0;
        int floor = pathFloor(z);
        int roof = floor + (room ? 14 : 6 + (int)((hash(0x721L, b.slot, 0) >>> 8) & 3));
        return new SideShape(hall || room, floor, roof);
    }

    private static int puddleWaterY(long seed, int z) {
        long cell = hash(seed ^ 0xADD1E, Math.floorDiv(z, 16), 0);
        int rootZ = Math.floorDiv(z, 16) * 16 + 5 + (int)(cell & 3);
        return Math.min(pathFloor(rootZ - 4), Math.min(pathFloor(rootZ), pathFloor(rootZ + 4))) + 2;
    }

    private static net.minecraft.core.Direction poolFacing(long seed, int x, int z) {
        long cell = hash(seed ^ 0xADD1E, Math.floorDiv(z, 16), 0);
        int rootZ = Math.floorDiv(z, 16) * 16 + 5 + (int)(cell & 3);
        double dx = x - riverCenter(rootZ) - 4;
        int dz = z - rootZ;
        return Math.abs(dx) > Math.abs(dz) ? (dx < 0 ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.EAST)
                : (dz < 0 ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH);
    }

    private static boolean joinedPillar(long seed, int x, int z, Column c) {
        if (!c.open || c.path || c.spider || z < START_Z + 32 || z > -24 || puddleShape(seed, x, z) <= 1.5) return false;
        long cell = hash(seed ^ 0xC011, Math.floorDiv(z, 32), 0);
        int rootZ = Math.floorDiv(z, 32) * 32 + 12 + (int)(cell & 7);
        double rootX = riverCenter(rootZ) + ((cell & 8) == 0 ? -23 : 9);
        double dx = x - rootX, dz = z - rootZ;
        return dx * dx + dz * dz <= 6.25;
    }

    private static double tunnelWidth(long seed, int z) {
        // Keep the full left-bank approach inside even the narrowest section.
        return Math.max(17.5, 20 + 2 * octaves(seed, z * .016, 0)
                - 2 * tunnelConstriction(seed, z));
    }

    private static double tunnelConstriction(long seed, int z) {
        return smooth((octaves(seed ^ 0x7A9E, z * .035, 0) + .3) / .65);
    }

    private static BlockState formation(boolean hanging, int remaining, int length, boolean waterlogged) {
        if (remaining >= 2 && length >= 5)
            return (hanging ? Asterion.SHADED_SHALE_FORMATION : Asterion.SHALE_FORMATION).defaultBlockState()
                    .setValue(ShaleFormationBlock.HANGING, hanging)
                    .setValue(ShaleFormationBlock.THICKNESS, Math.min(4, 2 + remaining * 2 / length))
                    .setValue(BlockStateProperties.WATERLOGGED, waterlogged);
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
        boolean mud = puddleShape(seed, x, z) <= 1.3;
        // Protect the entire approach, spawn and boat lane, including overhead clearance.
        if (column.path || column.spider || puddleShape(seed, x, z) <= 1.5 || (z >= 18 && Math.abs(offset) < 13) || z < START_Z + 8)
            return new Details(mud, 0, 0, 0, false);
        int cellX = Math.floorDiv(x, 9), cellZ = Math.floorDiv(z, 9);
        long cell = hash(seed ^ 0x571CE, cellX, cellZ);
        int rootX = cellX * 9 + 2 + (int)Math.floorMod(cell, 5);
        int rootZ = cellZ * 9 + 2 + (int)Math.floorMod(cell >>> 8, 5);
        int radius = Math.max(Math.abs(x - rootX), Math.abs(z - rootZ));
        boolean cluster = (cell & 3) != 0;
        int rock = cluster && radius <= 2 && column.floor >= WATER_Y - 3
                ? Math.max(0, 5 - radius * 2 - (int)(hash(seed, x, z) & 1)) : 0;
        // Broad full-block cones carry the silhouette; decorative spikes only finish a few tips.
        if (column.floor >= WATER_Y - 3)
            rock = Math.max(rock, blockSpire(seed ^ 0xB16, x, z));
        int spike = cluster && radius == 0 && rock > 0 ? 1 + (int)((cell >>> 16) & 1) : 0;
        int hanging = blockSpire(seed ^ 0xCE111, x, z);
        // Satellite teeth ring the full-height pillars, growing from both ends.
        if (z > START_Z + 16 && z < -24) {
            long pillar = hash(seed ^ 0xC011, Math.floorDiv(z, 32), 0);
            int pillarZ = Math.floorDiv(z, 32) * 32 + 12 + (int)(pillar & 7);
            double pillarX = riverCenter(pillarZ) + ((pillar & 8) == 0 ? -23 : 9);
            double distance = Math.max(Math.abs(x - pillarX), Math.abs(z - pillarZ));
            if (distance > 1 && distance < 4 && Math.abs(offset + 15) > 6) {
                int tooth = Math.max(1, (int)((4 - distance) * 3) - (int)(hash(seed, x, z) & 1));
                rock = Math.max(rock, tooth);
                hanging = Math.max(hanging, tooth + 1);
            }
        }
        int clearance = Math.max(0, column.roof - column.floor - 5);
        // Reserve room for both roof and floor silhouettes in the tighter passages.
        rock = Math.min(rock, Math.max(0, clearance * 2 / 3));
        spike = rock == 0 ? 0 : Math.min(spike, Math.max(0, clearance - rock));
        hanging = Math.min(hanging, Math.max(0, clearance - rock - spike));
        return new Details(mud, rock, spike, hanging, false);
    }

    /** Irregular shallow basins along the dry corridor, clear of the spawn and walking bank. */
    private static double puddleShape(long seed, int x, int z) {
        if (z < START_Z + 12 || z >= -20) return 100;
        int segment = Math.floorDiv(z, 16);
        long pool = hash(seed ^ 0xADD1E, segment, 0);
        int centerZ = segment * 16 + 5 + (int)(pool & 3);
        double centerX = riverCenter(centerZ) + 4;
        double dx = (x - centerX) / (2.2 + ((pool >>> 4) & 1) * .5);
        double dz = (z - centerZ) / (2.4 + ((pool >>> 5) & 1) * .6);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** World-space cones: inspect neighboring cells so large bases cross chunk edges intact. */
    private static int blockSpire(long seed, int x, int z) {
        int cellX = Math.floorDiv(x, 16), cellZ = Math.floorDiv(z, 16);
        int result = 0;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            int cx = cellX + dx, cz = cellZ + dz;
            long shape = hash(seed, cx, cz);
            if ((shape & 3) == 0) continue;
            int rootX = cx * 16 + (int)((shape >>> 4) & 15);
            int rootZ = cz * 16 + (int)((shape >>> 8) & 15);
            int radius = 5 + (int)((shape >>> 12) & 3);
            // Reject whole formations near the walking route; never slice their sides with paving.
            if (rootZ < 36 && Math.abs(rootX - (riverCenter(rootZ) - 15)) < radius + 6) continue;
            int rx = Math.abs(x - rootX), rz = Math.abs(z - rootZ);
            if (rx > radius || rz > radius) continue;
            // Angular diamond/octagon cross sections, with uneven stepped sides.
            double distance = Math.max(rx, rz) + Math.min(rx, rz) * .45;
            double taper = 1 - distance / (radius + .5);
            if (taper <= 0) continue;
            int height = 15 + (int)((shape >>> 16) & 15);
            int chips = distance < 1 ? 0 : (int)(hash(seed ^ shape, x, z) & 1);
            result = Math.max(result, Math.max(0, (int)Math.floor(height * taper * taper) - chips));
        }
        return result;
    }

    private static net.minecraft.core.Direction spireFacing(long seed, int x, int z) {
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
        int tallest = -1;
        for (net.minecraft.core.Direction candidate : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            int height = blockSpire(seed, x + candidate.getStepX(), z + candidate.getStepZ());
            if (height > tallest) { tallest = height; facing = candidate; }
        }
        return facing;
    }

    /** Paired guide lights at a steady twenty-block cadence, just outside the paving. */
    private static int pillarHeight(long seed, int x, int z, Column column) {
        if (!column.open || z < SPAWN_Z - 8 || z > FERRY_Z - 10 || column.floor <= WATER_Y || Math.floorMod(z - SPAWN_Z, 20) != 0) return 0;
        int center = (int)Math.round(z >= 12 ? landingX(z) : riverCenter(z) - 15);
        if (x != center - 3 && x != center + 3) return 0;
        int height = 3 + Math.floorMod(Math.floorDiv(z - SPAWN_Z, 20), 4);
        return column.roof - column.floor > height + 2 ? height : 0;
    }

    private record Details(boolean mud, int rock, int spike, int hanging, boolean waterfall) { }

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

    private record Column(boolean open, int floor, int roof, boolean path, boolean spider) { }
}
