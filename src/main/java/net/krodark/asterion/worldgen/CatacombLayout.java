package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Stateless, chunk-order-independent undercroft. The surface maze continues infinitely. */
public final class CatacombLayout {
    public static final int TILE = 19;
    public static final int FLOOR_Y = 22;
    public static final int WATER_Y = 23;
    public static final int CLEAR_HEIGHT = 31;
    public static final int ROOF_Y = AuthoredCatacombs.BASE_Y + 30;
    public static final int ROOT_X = 4, ROOT_Z = 4;
    public static final int ROOT_CENTER = ROOT_X * TILE + TILE / 2;
    public static final int BRAZIER_ROOM_MIN_X = 10, BRAZIER_ROOM_MAX_X = 12;
    public static final int BRAZIER_ROOM_MIN_Z = 4, BRAZIER_ROOM_MAX_Z = 6;
    public static final int BRAZIER_APPROACH_Z = 5;
    /** Guaranteed surface crossing on the direct branch between the root and boss room. */
    public static final int BRAZIER_APPROACH_CROSSING_X = 7;

    private CatacombLayout() { }

    public static boolean reserved(int tx, int tz) {
        return tx >= -4 && tx <= 3 && tz >= -4 && tz <= 3 || brazierRoom(tx, tz);
    }

    public static boolean brazierRoom(int tx, int tz) {
        return tx >= BRAZIER_ROOM_MIN_X && tx <= BRAZIER_ROOM_MAX_X
                && tz >= BRAZIER_ROOM_MIN_Z && tz <= BRAZIER_ROOM_MAX_Z;
    }

    public static int roofAt(int x, int z) {
        return Math.abs((long)x) <= AuthoredCatacombs.ARENA_RADIUS && Math.abs((long)z) <= AuthoredCatacombs.ARENA_RADIUS
                ? MinotaurArenaEntrances.FLOOR_Y - 1 : ROOF_Y;
    }

    public static boolean contains(BlockPos pos) {
        return pos.getY() >= 3 && pos.getY() <= Math.min(47, roofAt(pos.getX(), pos.getZ()));
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
        // A short guaranteed branch connects the authored boss room to the root.
        if (x == ROOT_X && z == BRAZIER_APPROACH_Z) return Direction.NORTH;
        if (z == BRAZIER_APPROACH_Z && x > ROOT_X && x < BRAZIER_ROOM_MIN_X) return Direction.WEST;
        // Route the existing infinite tree around the reserved 3x3 footprint. These
        // border cells keep descendants connected without opening accidental doors
        // through the authored room walls.
        if (x == BRAZIER_ROOM_MAX_X + 1 && z >= BRAZIER_ROOM_MIN_Z && z <= BRAZIER_ROOM_MAX_Z)
            return z <= BRAZIER_APPROACH_Z ? Direction.NORTH : Direction.SOUTH;
        if (z == BRAZIER_ROOM_MIN_Z - 1 && x > ROOT_X && x <= BRAZIER_ROOM_MAX_X + 1)
            return Direction.WEST;
        if (z == BRAZIER_ROOM_MAX_Z + 1 && x > ROOT_X && x <= BRAZIER_ROOM_MAX_X + 1)
            return Direction.WEST;
        Direction spine=backboneParent(seed,x,z);
        if (spine!=null) return spine;
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
        if (tx == BRAZIER_ROOM_MIN_X - 1 && tz == BRAZIER_APPROACH_Z && side == Direction.EAST) return true;
        if (tx == BRAZIER_ROOM_MIN_X && tz == BRAZIER_APPROACH_Z && side == Direction.WEST) return true;
        if (brazierRoom(nx, nz)) return false;
        return !reserved(tx, tz) && !reserved(nx, nz)
                && (parent(seed, tx, tz) == side || parent(seed, nx, nz) == side.getOpposite());
    }

    public static void generate(ChunkAccess chunk, long seed) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState brick=Asterion.ANCIENT_BRICKS.defaultBlockState();
        BlockState moss=Asterion.ANCIENT_MOSSY_BRICKS.defaultBlockState();
        for (int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)
            for (int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++)
                for (int y=3,roof=roofAt(x,z);y<=roof;y++)
                    chunk.setBlockState(cursor.set(x,y,z),
                            Math.floorMod(x*17L+y*3L+z*31L+seed,9)<3 ? moss : brick,0);
    }
}
