import java.lang.reflect.Method;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;

/** Exercises the actual terrain sampler without creating or modifying a saved world. */
public final class UnderworldTerrainSmoke {
    private static final Method SAMPLE;
    private static final Method OPEN;
    private static final Method FLOOR;
    private static final Method ROOF;
    private static final Method SPIDER;
    private static final Method PATH;
    static {
        try {
            SAMPLE = UnderworldTerrain.class.getDeclaredMethod("column", long.class, int.class, int.class, java.util.Map.class);
            SAMPLE.setAccessible(true);
            Class<?> column = SAMPLE.getReturnType();
            OPEN = column.getDeclaredMethod("open");
            FLOOR = column.getDeclaredMethod("floor");
            ROOF = column.getDeclaredMethod("roof");
            SPIDER = column.getDeclaredMethod("spider");
            PATH = column.getDeclaredMethod("path");
            OPEN.setAccessible(true);
            FLOOR.setAccessible(true);
            ROOF.setAccessible(true);
            SPIDER.setAccessible(true);
            PATH.setAccessible(true);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private static final java.util.Map<Long, java.util.Map<Long, Object>> NODES = new java.util.HashMap<>();
    private static Object sample(long seed, int x, int z) throws Exception {
        return SAMPLE.invoke(null, seed, x, z, NODES.computeIfAbsent(seed, ignored -> new java.util.HashMap<>()));
    }

    private static void check(long seed, int x, int z, boolean dry) throws Exception {
        Object column = sample(seed, x, z);
        int floor = (int)FLOOR.invoke(column), roof = (int)ROOF.invoke(column);
        Method dock = UnderworldTerrain.class.getDeclaredMethod("dockColumn", int.class, int.class);
        dock.setAccessible(true);
        boolean boardwalk = (boolean)dock.invoke(null, x, z);
        if (!(boolean)OPEN.invoke(column) || roof < Math.max(floor, UnderworldTerrain.WATER_Y) + 8
                || (dry ? (!boardwalk && (floor < UnderworldTerrain.WATER_Y + 1 || floor > UnderworldTerrain.WATER_Y + 6)) : floor >= UnderworldTerrain.WATER_Y - 3))
            throw new AssertionError("seed=" + seed + " x=" + x + " z=" + z
                    + " dry=" + dry + " floor=" + floor + " roof=" + roof);
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        for (int i = 0; i < 64; i++) {
            long seed = i * 0x9E3779B97F4A7C15L;
            check(seed, UnderworldTerrain.SPAWN_X, UnderworldTerrain.SPAWN_Z, true);
            for (int z = UnderworldTerrain.SPAWN_Z; z < 12; z++)
                check(seed, (int)Math.round(UnderworldTerrain.riverCenter(z) - 15), z, true);
            for (int z = 12; z <= UnderworldTerrain.FERRY_Z + 4; z++) {
                Method landing = UnderworldTerrain.class.getDeclaredMethod("landingX", int.class);
                landing.setAccessible(true);
                check(seed, (int)Math.round((double)landing.invoke(null, z)), z, true);
            }
            for (int z = UnderworldTerrain.SPAWN_Z; z <= UnderworldTerrain.END_Z; z++)
                for (double side : new double[]{-1.0625, 0, 1.0625})
                    if (z < 18 || z >= 48)
                        check(seed, (int)Math.floor(UnderworldTerrain.riverCenter(z) + side), z, z < 18);
            int previous = Integer.MIN_VALUE;
            for (int z = 16; z <= 48; z++) {
                Object c = sample(seed, (int)Math.round(UnderworldTerrain.riverCenter(z)), z);
                int floor = (int)FLOOR.invoke(c);
                if (previous != Integer.MIN_VALUE && Math.abs(floor - previous) > 2)
                    throw new AssertionError("Abrupt bay trench at " + z);
                previous = floor;
            }
            for (int x : new int[]{-10000, -512, 0, 512, 10000})
                for (int z : new int[]{200, 1024, 4096, 10000}) check(seed, x, z, false);
        }
        Method palette = UnderworldTerrain.class.getDeclaredMethod("pathMaterial", long.class, int.class, int.class);
        palette.setAccessible(true);
        int[] materials = new int[4];
        for (int seed = 0; seed < 64; seed++) for (int z = -112; z < 12; z++) {
            double route = UnderworldTerrain.riverCenter(z) - 15 + Math.sin(z * .075) * .65;
            int core = (int)palette.invoke(null, (long)seed, (int)Math.round(route), z);
            if (core > 1) throw new AssertionError("Missing deadstone path center");
            for (int dx = -4; dx <= 4; dx++)
                materials[(int)palette.invoke(null, (long)seed, (int)Math.round(route) + dx, z)]++;
        }
        for (int count : materials) if (count < 100) throw new AssertionError("Missing path palette variant");
        System.out.println("PASS both deadstone and shale variants, continuous deadstone center");
        Method dockShape = UnderworldTerrain.class.getDeclaredMethod("dockColumn", int.class, int.class);
        dockShape.setAccessible(true);
        int deckArea = 0;
        for (int z = 12; z <= UnderworldTerrain.FERRY_Z + 8; z++) {
            int row = 0;
            for (int x = -60; x < 60; x++) if ((boolean)dockShape.invoke(null, x, z)) {
                row++; deckArea++;
                for (int seed = 0; seed < 64; seed++) {
                    Object c = sample(seed, x, z);
                    if ((int)FLOOR.invoke(c) >= UnderworldTerrain.WATER_Y)
                        throw new AssertionError("Dock rests on land");
                }
                if (x + 1 > UnderworldTerrain.riverCenter(z) - 1.25)
                    throw new AssertionError("Dock intrudes into ferry lane");
            }
            if (row != 0 && row != 7) throw new AssertionError("Dock width is not seven blocks");
            if (row > 0 && z < UnderworldTerrain.FERRY_Z - 6) throw new AssertionError("Long land boardwalk returned");
        }
        if (deckArea != 77) throw new AssertionError("Dock is not seven by eleven: " + deckArea);
        System.out.println("PASS compact 7x11 dock above water, clear ferry lane across 64 seeds");
        Method pathLevel = UnderworldTerrain.class.getDeclaredMethod("pathFloor", int.class);
        pathLevel.setAccessible(true);
        int changes = 0;
        for (int z = UnderworldTerrain.SPAWN_Z; z < 20; z++) {
            int h = (int)pathLevel.invoke(null, z), next = (int)pathLevel.invoke(null, z + 1);
            if (Math.abs(next - h) > 1) throw new AssertionError("Unwalkable path step");
            if (next != h) changes++;
        }
        if (changes < 4) throw new AssertionError("Missing terraced path elevations");
        // The dry tunnel should have compact chambers and visibly tapered sections.
        Method width = UnderworldTerrain.class.getDeclaredMethod("tunnelWidth", long.class, int.class);
        width.setAccessible(true);
        double narrowest = Double.MAX_VALUE, widest = 0;
        int lowest = Integer.MAX_VALUE, highest = 0;
        for (int seed = 0; seed < 64; seed++) for (int z = -158; z < -36; z++) {
            double w = (double)width.invoke(null, (long)seed, z);
            if (w < 17.5 || w > 22) throw new AssertionError("Tunnel width outside compact bounds: " + w);
            narrowest = Math.min(narrowest, w); widest = Math.max(widest, w);
            Object c = sample(seed, (int)Math.round(UnderworldTerrain.riverCenter(z)), z);
            int height = (int)ROOF.invoke(c) - (int)FLOOR.invoke(c);
            if (height < 8 || height > 45) throw new AssertionError("Tunnel vault outside compact bounds: " + height);
            lowest = Math.min(lowest, height); highest = Math.max(highest, height);
        }
        if (widest - narrowest < 2 || highest - lowest < 5)
            throw new AssertionError("Tunnel lacks tapered sections");
        System.out.println("PASS compact, tapered tunnel: half-width " + narrowest + ".." + widest
                + ", center clearance " + lowest + ".." + highest);
        Method puddle = UnderworldTerrain.class.getDeclaredMethod("puddleShape", long.class, int.class, int.class);
        puddle.setAccessible(true);
        Method waterY = UnderworldTerrain.class.getDeclaredMethod("puddleWaterY", long.class, int.class);
        waterY.setAccessible(true);
        int wet = 0, rims = 0;
        for (int z = -158; z < -20; z++) for (int x = -40; x < 40; x++) {
            double shape = (double)puddle.invoke(null, 42L, x, z);
            if (shape <= 1.03 && Math.abs(x - UnderworldTerrain.riverCenter(z)) > 4) {
                Object c = sample(42L, x, z);
                if (!(boolean)SPIDER.invoke(c) && !(boolean)PATH.invoke(c)
                        && (int)FLOOR.invoke(c) != (int)waterY.invoke(null, 42L, z) - 1)
                    throw new AssertionError("Puddle basin is not carved");
                if (shape <= .72) wet++; else rims++;
            }
        }
        if (wet < 20 || rims < 20) throw new AssertionError("Missing water basins or stair rims");
        System.out.println("PASS puddles: " + wet + " inner slab cells, " + rims + " outer slab cells");
        Method spire = UnderworldTerrain.class.getDeclaredMethod("blockSpire", long.class, int.class, int.class);
        spire.setAccessible(true);
        int broad = 0, giant = 0, crossing = 0;
        for (int z = -96; z < 96; z++) for (int x = -96; x < 96; x++) {
            int h = (int)spire.invoke(null, 42L, x, z);
            if (h >= 16) giant++;
            if (h >= 4 && (int)spire.invoke(null, 42L, x + 1, z) >= 4) broad++;
            if (Math.floorMod(x, 16) == 15 && h > 0
                    && (int)spire.invoke(null, 42L, x + 1, z) > 0) crossing++;
        }
        if (giant < 10 || broad < 100 || crossing < 10)
            throw new AssertionError("Missing large, broad, chunk-crossing block spires");
        System.out.println("PASS solid spires: " + giant + " tall cores, " + broad + " broad columns, " + crossing + " boundary crossings");
        Method details = UnderworldTerrain.class.getDeclaredMethod("details", long.class, int.class, int.class, SAMPLE.getReturnType());
        details.setAccessible(true);
        Class<?> shape = details.getReturnType();
        Method mud = shape.getDeclaredMethod("mud"), rock = shape.getDeclaredMethod("rock"),
                spike = shape.getDeclaredMethod("spike"), hanging = shape.getDeclaredMethod("hanging"),
                waterfall = shape.getDeclaredMethod("waterfall");
        for (Method m : new Method[]{mud, rock, spike, hanging, waterfall}) m.setAccessible(true);
        Method pillar = UnderworldTerrain.class.getDeclaredMethod("pillarHeight", long.class, int.class, int.class, SAMPLE.getReturnType());
        pillar.setAccessible(true);
        Method joined = UnderworldTerrain.class.getDeclaredMethod("joinedPillar", long.class, int.class, int.class, SAMPLE.getReturnType());
        joined.setAccessible(true);
        int joinedColumns = 0;
        for (int z = UnderworldTerrain.SPAWN_Z; z <= 12; z += 20) {
            int center = (int)Math.round(UnderworldTerrain.riverCenter(z) - 15);
            for (int x : new int[]{center - 3, center + 3}) {
                Object c = sample(42L, x, z);
                if ((int)pillar.invoke(null, 42L, x, z, c) < 3)
                    throw new AssertionError("Missing regular path torch at " + x + "," + z);
            }
        }
        int narrowCap = 0, wideCap = 0;
        for (int x = -60; x < 60; x++) {
            if (Math.abs(x - (UnderworldTerrain.riverCenter(UnderworldTerrain.START_Z + 1) - 5)) < 12
                    && (boolean)OPEN.invoke(sample(42L, x, UnderworldTerrain.START_Z + 1))) narrowCap++;
            if (Math.abs(x - (UnderworldTerrain.riverCenter(UnderworldTerrain.START_Z + 30) - 5)) < 12
                    && (boolean)OPEN.invoke(sample(42L, x, UnderworldTerrain.START_Z + 30))) wideCap++;
        }
        if (narrowCap >= wideCap * .75)
            throw new AssertionError("Entrance does not taper: " + narrowCap + " / " + wideCap);
        int pillars = 0, heights = 0;
        int muddy=0, spikes=0, curtains=0, drops=0, tallest=0;
        for (int seed=0;seed<8;seed++) for (int z=-158;z<60;z++) for (int x=-60;x<=60;x++) {
            Object c=sample(seed, x,z);
            if (!(boolean)OPEN.invoke(c)) continue;
            if ((boolean)joined.invoke(null, (long)seed, x, z, c)) joinedColumns++;
            int light = (int)pillar.invoke(null, (long)seed, x,z,c);
            if (light > 0) {
                if (light < 3 || light > 6 || (int)FLOOR.invoke(c) <= UnderworldTerrain.WATER_Y
                        || (int)ROOF.invoke(c) <= (int)FLOOR.invoke(c) + light + 2)
                    throw new AssertionError("Unsafe fire pillar");
                pillars++; heights |= 1 << light;
            }
            Object d=details.invoke(null, (long)seed, x,z,c);
            int r=(int)rock.invoke(d), s=(int)spike.invoke(d), h=(int)hanging.invoke(d);
            if(s>0&&r==0)throw new AssertionError("Floating ground spike at "+x+","+z);
            if(r+s+h>Math.max(0,(int)ROOF.invoke(c)-(int)FLOOR.invoke(c)-5))
                throw new AssertionError("Formation blocks passage clearance");
            if((boolean)mud.invoke(d))muddy++;
            if(s>0)spikes++;
            if(h>0)curtains++;
            if((boolean)waterfall.invoke(d))drops++;
            tallest=Math.max(tallest,(int)ROOF.invoke(c)-(int)FLOOR.invoke(c));
        }
        if (joinedColumns < 100) throw new AssertionError("Missing thick floor-to-roof columns");
        System.out.println("PASS rounded entrance, paired torches every 20 blocks, and " + joinedColumns + " joined pillar columns");
        if (pillars < 20 || heights != 120) throw new AssertionError("Missing 3-6 block pillar variation: " + pillars + "/" + heights);
        if(muddy<100||spikes<20||curtains<20||drops!=0||tallest<60)
            throw new AssertionError("Missing cave variety: "+muddy+","+spikes+","+curtains+","+drops+","+tallest);
        System.out.println("PASS rooted formations, isolated puddles, muddy banks and tall chambers: "
                +muddy+" mud, "+spikes+" spikes, "+curtains+" hanging, "+drops+" waterfall columns.");
        int highSpiderColumns = 0;
        for (int seed = 0; seed < 8; seed++) {
            for (int slot = 0; slot < 8; slot++) {
                var chamber = UnderworldTerrain.chamberCenter(slot);
                Object c = sample(seed, chamber.getX(), chamber.getZ());
                if (!(boolean)OPEN.invoke(c) || (int)ROOF.invoke(c) - (int)FLOOR.invoke(c) < 18)
                    throw new AssertionError("Spider chamber is missing or too low at slot " + slot);
            }
            for (int z = -760; z < -80; z += 13) for (int x = -80; x <= 80; x += 7) {
                if (Math.abs(x - (UnderworldTerrain.riverCenter(z) - 15)) < 32) continue;
                Object c = sample(seed, x, z);
                if ((boolean)OPEN.invoke(c) && (boolean)SPIDER.invoke(c)
                        && (int)ROOF.invoke(c) - (int)FLOOR.invoke(c) >= 24) highSpiderColumns++;
            }
        }
        if (highSpiderColumns < 500) throw new AssertionError("Spider cave vaults are too sparse: " + highSpiderColumns);
        System.out.println("PASS linked spider chambers and " + highSpiderColumns + " tall side-cave samples");
        System.out.println("PASS: 64 seeds; safe spawn, continuous bank/landing, clear ferry route, unbounded sea.");
    }
}
