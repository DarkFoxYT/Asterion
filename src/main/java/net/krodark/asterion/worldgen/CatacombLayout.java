package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import java.util.List;

/** Stateless, chunk-order-independent undercroft. The surface maze continues infinitely. */
public final class CatacombLayout {
    public static final int TILE = 19;
    public static final int FLOOR_Y = LabyrinthLevels.CATACOMB_BASE_Y + 3;
    public static final int WATER_Y = FLOOR_Y + 1;
    public static final int CLEAR_HEIGHT = 31;
    public static final int ROOF_Y = AuthoredCatacombs.BASE_Y + 30;
    public static final int ROOT_X = 4, ROOT_Z = 4;
    public static final int ROOT_CENTER = ROOT_X * TILE + TILE / 2;
    public static final int BRAZIER_ROOM_MIN_X = 10, BRAZIER_ROOM_MAX_X = 12;
    public static final int BRAZIER_ROOM_MIN_Z = 4, BRAZIER_ROOM_MAX_Z = 6;
    public static final int BRAZIER_APPROACH_Z = 5;
    /** Three authored chambers on parallel eastward branches, separated by solid crypt. */
    public static final List<Integer> BRAZIER_ROOM_MIN_ZS = List.of(4, 10, 16);
    /** Guaranteed surface crossing on the direct branch between the root and boss room. */
    public static final int BRAZIER_APPROACH_CROSSING_X = 7;

    private CatacombLayout() { }

    public static boolean reserved(int tx, int tz) {
        return tx >= -4 && tx <= 3 && tz >= -4 && tz <= 3 || brazierRoom(tx, tz);
    }

    public static boolean brazierRoom(int tx, int tz) {
        return brazierRoomIndex(tx, tz) >= 0;
    }

    public static int brazierRoomIndex(int tx, int tz) {
        if (tx < BRAZIER_ROOM_MIN_X || tx > BRAZIER_ROOM_MAX_X) return -1;
        for (int index = 0; index < BRAZIER_ROOM_MIN_ZS.size(); index++) {
            int minZ = BRAZIER_ROOM_MIN_ZS.get(index);
            if (tz >= minZ && tz <= minZ + 2) return index;
        }
        return -1;
    }

    private static boolean brazierApproach(int z) {
        return BRAZIER_ROOM_MIN_ZS.stream().anyMatch(minZ -> z == minZ + 1);
    }

    public static int roofAt(int x, int z) {
        return Math.abs((long)x) <= AuthoredCatacombs.ARENA_RADIUS && Math.abs((long)z) <= AuthoredCatacombs.ARENA_RADIUS
                ? MinotaurArenaEntrances.FLOOR_Y - 1 : ROOF_Y;
    }

    public static boolean contains(BlockPos pos) {
        return pos.getY() >= AuthoredCatacombs.ARENA_BASE_Y + 2
                && pos.getY() <= Math.min(LabyrinthLevels.MAZE_FLOOR_Y - 1,
                roofAt(pos.getX(), pos.getZ()));
    }

    // Junctions are three modules apart. Only selected tree edges get corridors;
    // the rest stays solid, rather than carving a room into every grid cell.
    private static final int SPACING = 3;
    public static long hash(long seed, int x, int z) {
        long h = seed ^ x * 0x632BE59BD9B4E019L ^ z * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
    private static Direction junctionParent(long seed, int x, int z) {
        if (reserved(x,z) || x == ROOT_X && z == ROOT_Z) return null;
        Direction horizontal = x < ROOT_X ? Direction.EAST : Direction.WEST;
        Direction vertical = z < ROOT_Z ? Direction.SOUTH : Direction.NORTH;
        boolean canX = x != ROOT_X && !reserved(x + horizontal.getStepX()*SPACING,z);
        boolean canZ = z != ROOT_Z && !reserved(x,z + vertical.getStepZ()*SPACING);
        return canX && (!canZ || (hash(seed,x,z)&1)==0) ? horizontal : vertical;
    }
    private static Direction backboneParent(long seed, int x, int z) {
        if (reserved(x,z)) return null;
        int rx = Math.floorMod(x-ROOT_X,SPACING), rz = Math.floorMod(z-ROOT_Z,SPACING);
        if (rx==0 && rz==0) return junctionParent(seed,x,z);
        if (rz==0) {
            if (junctionParent(seed,x-rx,z)==Direction.EAST) return Direction.EAST;
            if (junctionParent(seed,x-rx+SPACING,z)==Direction.WEST) return Direction.WEST;
        } else if (rx==0) {
            if (junctionParent(seed,x,z-rz)==Direction.SOUTH) return Direction.SOUTH;
            if (junctionParent(seed,x,z-rz+SPACING)==Direction.NORTH) return Direction.NORTH;
        }
        return null;
    }
    /** A bounded local calculation: every occupied cell has a route to the arena root. */
    public static Direction parent(long seed, int x, int z) {
        if (reserved(x,z) || x==ROOT_X && z==ROOT_Z) return null;
        // The arena jigsaw feeds the first authored module at (0, 4). Keep a short
        // authored-module branch from there to the root instead of a hand-built hall.
        if (z == ROOT_Z && x >= 0 && x < ROOT_X) return Direction.EAST;
        // A straight north/south crypt spine feeds three straight east/west boss halls.
        int finalApproach = BRAZIER_ROOM_MIN_ZS.getLast() + 1;
        if (x == ROOT_X && z > ROOT_Z && z <= finalApproach) return Direction.NORTH;
        if (brazierApproach(z) && x > ROOT_X && x < BRAZIER_ROOM_MIN_X) return Direction.WEST;
        // Route the infinite tree around every reserved 3x3 room footprint without
        // opening accidental diagonal or side entrances through the authored walls.
        for (int minZ : BRAZIER_ROOM_MIN_ZS) {
            int approachZ = minZ + 1, maxZ = minZ + 2;
            if (x == BRAZIER_ROOM_MAX_X + 1 && z >= minZ && z <= maxZ)
                return z <= approachZ ? Direction.NORTH : Direction.SOUTH;
            if ((z == minZ - 1 || z == maxZ + 1) && x > ROOT_X && x <= BRAZIER_ROOM_MAX_X + 1)
                return Direction.WEST;
        }
        Direction spine=backboneParent(seed,x,z);
        if (spine!=null) return spine;
        // A stair replaces this module when present, or adds a leaf to the adjacent junction.
        if (ForgeDepths.isStairModule(x, z)) return Direction.EAST;
        if (Math.floorMod(x-ROOT_X,SPACING)==0 || Math.floorMod(z-ROOT_Z,SPACING)==0) return null;
        long roll=hash(seed ^ 0xD1B54A32D192ED03L,x,z);
        if (Math.floorMod(roll,6)!=0) return null;
        // Small blind side branches, each attached to exactly one existing passage.
        Direction[] sides={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
        int start=(int)((roll>>>8)&3);
        for(int i=0;i<4;i++) {
            Direction side=sides[(start+i)&3];
            int nx=x+side.getStepX(),nz=z+side.getStepZ();
            if(backboneParent(seed,nx,nz)!=null) return side;
        }
        return null;
    }
    public static boolean occupied(long seed,int x,int z) {
        return !reserved(x,z) && !brazierRoom(x,z)
                && (x==ROOT_X && z==ROOT_Z || parent(seed,x,z)!=null);
    }

    public static boolean connected(long seed, int tx, int tz, Direction side) {
        int nx = tx + side.getStepX(), nz = tz + side.getStepZ();
        if (tx == BRAZIER_ROOM_MIN_X - 1 && brazierApproach(tz) && side == Direction.EAST) return true;
        if (tx == BRAZIER_ROOM_MIN_X && brazierApproach(tz) && side == Direction.WEST) return true;
        if (brazierRoom(nx, nz)) return false;
        if (reserved(tx, tz) || reserved(nx, nz)) return false;
        Direction hereParent = parent(seed, tx, tz);
        Direction thereParent = parent(seed, nx, nz);
        if (hereParent == side || thereParent == side.getOpposite()) return true;
        return wovenConnection(seed, tx, tz, side);
    }

    /** A secondary edge that turns adjacent side branches into a coherent loop. */
    public static boolean wovenConnection(long seed, int tx, int tz, Direction side) {
        if (!side.getAxis().isHorizontal()) return false;
        int nx = tx + side.getStepX(), nz = tz + side.getStepZ();
        if (reserved(tx, tz) || reserved(nx, nz) || brazierRoom(tx, tz) || brazierRoom(nx, nz)
                || !occupied(seed, tx, tz) || !occupied(seed, nx, nz)) return false;
        if (parent(seed, tx, tz) == side || parent(seed, nx, nz) == side.getOpposite()) return false;
        // The parent edges guarantee reachability. Weave nearby side branches back into
        // that tree to create readable loops and alternate routes instead of endless
        // isolated dead ends. Backbone-only links stay sparse and visually deliberate.
        if (backboneParent(seed, tx, tz) != null && backboneParent(seed, nx, nz) != null)
            return false;
        int edgeX = Math.min(tx, nx), edgeZ = Math.min(tz, nz);
        long axisSalt = tx == nx ? 0x94D049BB133111EBL : 0xD1B54A32D192ED03L;
        return (hash(seed ^ axisSalt, edgeX, edgeZ) & 1L) == 0L;
    }

    public static void generate(ChunkAccess chunk, long seed) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState brick=Asterion.ANCIENT_BRICKS.defaultBlockState();
        BlockState moss=Asterion.ANCIENT_MOSSY_BRICKS.defaultBlockState();
        for (int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)
            for (int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++)
                for (int y=AuthoredCatacombs.ARENA_BASE_Y+2,roof=roofAt(x,z);y<=roof;y++) {
                    double radius = Math.hypot(x, z);
                    if (radius < AuthoredCatacombs.ARENA_RADIUS - 4) {
                        chunk.setBlockState(cursor.set(x,y,z), Blocks.AIR.defaultBlockState(),0);
                        continue;
                    }
                    int band = (int)Math.floor(radius - (AuthoredCatacombs.ARENA_RADIUS - 4));
                    BlockState rim = band < 2 ? Asterion.SHADED_SHALE.defaultBlockState()
                            : band < 5 ? Asterion.SHALE.defaultBlockState()
                            : (Math.floorMod(x*17L+y*3L+z*31L+seed,9)<3 ? moss : brick);
                    chunk.setBlockState(cursor.set(x,y,z), rim,0);
                }
    }
}

