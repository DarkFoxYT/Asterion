import java.lang.reflect.Method;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;

/** Exercises the actual terrain sampler without creating or modifying a saved world. */
public final class UnderworldTerrainSmoke {
    private static final Method SAMPLE;
    private static final Method OPEN;
    private static final Method FLOOR;
    private static final Method ROOF;
    static {
        try {
            SAMPLE = UnderworldTerrain.class.getDeclaredMethod("column", long.class, int.class, int.class);
            SAMPLE.setAccessible(true);
            Class<?> column = SAMPLE.getReturnType();
            OPEN = column.getDeclaredMethod("open");
            FLOOR = column.getDeclaredMethod("floor");
            ROOF = column.getDeclaredMethod("roof");
            OPEN.setAccessible(true);
            FLOOR.setAccessible(true);
            ROOF.setAccessible(true);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private static void check(long seed, int x, int z, boolean dry) throws Exception {
        Object column = SAMPLE.invoke(null, seed, x, z);
        int floor = (int)FLOOR.invoke(column), roof = (int)ROOF.invoke(column);
        if (!(boolean)OPEN.invoke(column) || roof < Math.max(floor, UnderworldTerrain.WATER_Y) + 8
                || (dry ? floor != UnderworldTerrain.WATER_Y + 1 : floor >= UnderworldTerrain.WATER_Y - 3))
            throw new AssertionError("seed=" + seed + " x=" + x + " z=" + z
                    + " dry=" + dry + " floor=" + floor + " roof=" + roof);
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 64; i++) {
            long seed = i * 0x9E3779B97F4A7C15L;
            check(seed, UnderworldTerrain.SPAWN_X, UnderworldTerrain.SPAWN_Z, true);
            for (int z = UnderworldTerrain.SPAWN_Z; z <= 35; z++)
                check(seed, (int)Math.round(UnderworldTerrain.riverCenter(z) - 15), z, true);
            for (int z = 12; z <= UnderworldTerrain.FERRY_Z + 4; z++) {
                double t = Math.clamp((z - 12.0) / (UnderworldTerrain.FERRY_Z - 12.0), 0, 1);
                t = t * t * (3 - 2 * t);
                check(seed, (int)Math.round(UnderworldTerrain.riverCenter(z) - 15 + t * 11.5), z, true);
            }
            for (int z = UnderworldTerrain.SPAWN_Z; z <= UnderworldTerrain.END_Z; z++)
                for (double side : new double[]{-1.0625, 0, 1.0625})
                    check(seed, (int)Math.floor(UnderworldTerrain.riverCenter(z) + side), z, false);
            for (int x : new int[]{-10000, -512, 0, 512, 10000})
                for (int z : new int[]{200, 1024, 4096, 10000}) check(seed, x, z, false);
        }
        System.out.println("PASS: 64 seeds; safe spawn, continuous bank/landing, clear ferry route, unbounded sea.");
    }
}
