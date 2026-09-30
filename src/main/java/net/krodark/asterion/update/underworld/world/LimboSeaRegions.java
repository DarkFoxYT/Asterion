package net.krodark.asterion.update.underworld.world;

import net.minecraft.world.phys.Vec3;

/** Five connected seas, never repeating noise islands. The docks always start in Styx. */
public final class LimboSeaRegions {
    public static final int REGION_SIZE=3200;
    public static final int BLEND_HALF_WIDTH=320;
    public enum Sea { STYX, PHLEGETHON, LETHE, ACHERON, COCYTUS }
    private static final Vec3[] WATER={new Vec3(.0045,.0052,.0058),new Vec3(.14,.014,.004),
            new Vec3(.24,.205,.15),new Vec3(.018,.095,.031),new Vec3(.075,.095,.115)};
    private static final Vec3[] FOG={new Vec3(.034,.038,.044),new Vec3(.14,.035,.018),
            new Vec3(.29,.265,.215),new Vec3(.09,.16,.075),new Vec3(.23,.265,.29)};
    private LimboSeaRegions() { }
    public static double distance(double x,double z) {
        if(z<18)return 0;
        return Math.max(0,Math.hypot(x,z-UnderworldTerrain.FERRY_Z)
                +80*Math.sin(x*.0007)+80*Math.sin(z*.0009));
    }
    public static Sea sea(double x,double z) {
        return Sea.values()[Math.clamp((int)(distance(x,z)/REGION_SIZE),0,4)];
    }
    public static double transition(double x,double z,int boundary) {
        double t=Math.clamp((distance(x,z)-(boundary*REGION_SIZE-BLEND_HALF_WIDTH))/(2*BLEND_HALF_WIDTH),0,1);
        return t*t*t*(t*(t*6-15)+10);
    }
    private static Vec3 blend(Vec3[] palette,double x,double z) {
        Vec3 color=palette[0];
        for(int i=1;i<5;i++)color=color.lerp(palette[i],transition(x,z,i));
        return color;
    }
    public static Vec3 water(double x,double z) { return blend(WATER,x,z); }
    public static Vec3 fog(double x,double z) { return blend(FOG,x,z); }
    public static double fire(double x,double z) { return transition(x,z,1)*(1-transition(x,z,2)); }
}
