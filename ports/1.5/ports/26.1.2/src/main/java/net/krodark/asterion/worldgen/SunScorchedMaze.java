package net.krodark.asterion.worldgen;

/** Seeded radial ruins around the sun; open gates keep every annulus traversable. */
public final class SunScorchedMaze {
    private SunScorchedMaze() { }
    public static boolean region(int x,int z,int cell) {double r=Math.hypot(x+.5,z+.5);return r>cell*3.0 && r<100;}
    public static boolean wall(long seed,int x,int z,int cell,int thickness) {
        double r=Math.hypot(x+.5,z+.5),angle=Math.atan2(z+.5,x+.5);
        double spacing=cell*1.45;int ring=(int)Math.round(r/spacing);
        double shift=((mix(seed+ring*7919L)>>>11)*0x1.0p-53-.5)*.45;
        double gate=Math.abs(Math.sin(angle*3+shift+ring*.46));
        // Six broad, staggered entrances with long broken sections in the scorched inner rings.
        if(gate<Math.min(.48,4.0/Math.max(1,r)*3))return false;
        return Math.abs(r-ring*spacing)<thickness*.52;
    }
    public static int height(long seed,int x,int z,int normal) {
        double r=Math.hypot(x+.5,z+.5);
        double damage=Math.pow(Math.max(0,1-r/100),.7);
        double chips=Math.sin(x*.24+Math.sin(z*.13))*Math.cos(z*.21+Math.sin(x*.1));
        double approach = Math.max(0, Math.min(1, (Math.max(Math.abs(x+.5), Math.abs(z+.5))
                - AuthoredCatacombs.ARENA_RADIUS) / 24.0));
        approach = approach * approach * (3 - 2 * approach);
        return (int)Math.round(Math.max(3,Math.min(normal,(int)Math.round(normal*(1-damage*.85)+chips*damage*5))) * approach);
    }
    public static double warp(long seed,int x,int z,boolean horizontal,int cell) {
        double phase=(mix(seed+(horizontal?19:43))>>>11)*0x1.0p-53*Math.PI*2;
        return Math.sin((horizontal?z:x)/(cell*7.0)+phase)*cell*2.3
                +Math.sin((x+z)/(cell*13.0)-phase)*cell*1.1;
    }
    public static long mix(long v){v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;return v^(v>>>31);}
}
