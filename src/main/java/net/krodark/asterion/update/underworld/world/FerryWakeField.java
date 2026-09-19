package net.krodark.asterion.update.underworld.world;

import java.util.ArrayDeque;
import java.util.Arrays;

/** A bounded history in world coordinates. Moving the sampling window never moves a wake. */
public final class FerryWakeField {
    public static final int SIZE = 128;
    public static final double EXTENT = 64, LIFETIME = 360;
    private record Stamp(double x, double z, double dx, double dz, double time, double strength) { }
    private final ArrayDeque<Stamp> stamps = new ArrayDeque<>();
    private final float[] foam = new float[SIZE * SIZE], height = new float[SIZE * SIZE];
    private double lastX = Double.NaN, lastZ, lastTime;
    public int originX, originZ;

    public void clear() { stamps.clear(); lastX = Double.NaN; Arrays.fill(foam, 0); Arrays.fill(height, 0); }
    public int size() { return stamps.size(); }
    public void record(double x, double z, double time, double immersion) {
        if (!Double.isFinite(lastX)) { lastX=x; lastZ=z; lastTime=time; return; }
        double dx=x-lastX, dz=z-lastZ, distance=Math.hypot(dx,dz), elapsed=time-lastTime;
        if (elapsed<=0 || distance>8 || elapsed>40) { lastX=x; lastZ=z; lastTime=time; return; }
        if (distance < .45) return;
        double speed=distance/elapsed;
        stamps.addLast(new Stamp(x,z,dx/distance,dz/distance,time,Math.clamp(speed/.072,0,1.5)*immersion));
        while(stamps.size()>64) stamps.removeFirst();
        lastX=x; lastZ=z; lastTime=time;
    }
    public void rasterize(double centerX, double centerZ, double time) {
        originX=(int)Math.floor((centerX-EXTENT*.5)/8)*8;
        originZ=(int)Math.floor((centerZ-EXTENT*.5)/8)*8;
        Arrays.fill(foam,0); Arrays.fill(height,0);
        stamps.removeIf(s -> time-s.time > LIFETIME || time < s.time);
        for(Stamp s:stamps) {
            double age=time-s.time, fade=Math.pow(1-age/LIFETIME,2)*s.strength;
            double spread=.75+age*.012, width=.55+age*.003;
            for(int side=-1;side<=1;side+=2) {
                double cx=s.x+s.dz*spread*side, cz=s.z-s.dx*spread*side;
                int x0=Math.max(0,(int)Math.floor((cx-originX-width*3)*2));
                int x1=Math.min(SIZE-1,(int)Math.ceil((cx-originX+width*3)*2));
                int z0=Math.max(0,(int)Math.floor((cz-originZ-width*3)*2));
                int z1=Math.min(SIZE-1,(int)Math.ceil((cz-originZ+width*3)*2));
                for(int z=z0;z<=z1;z++) for(int x=x0;x<=x1;x++) {
                    double rx=originX+(x+.5)*.5-cx, rz=originZ+(z+.5)*.5-cz;
                    double radius=Math.hypot(rx,rz)/width;
                    if(radius>3)continue;
                    double density=Math.exp(-radius*radius*1.5)*fade;
                    int at=z*SIZE+x;
                    foam[at]=Math.max(foam[at],(float)density);
                    height[at]+=(float)(.026*Math.cos(radius*3-age*.055)*density);
                }
            }
        }
    }
    public int pixel(int x,int z) {
        int i=z*SIZE+x;
        int r=(int)Math.round(Math.clamp(foam[i],0,1)*255);
        int g=(int)Math.round(128+Math.clamp(height[i],-.1,.1)*1000);
        return 0xff000000 | r<<16 | g<<8;
    }
    public float foamAt(int x,int z) { return foam[z*SIZE+x]; }
}
