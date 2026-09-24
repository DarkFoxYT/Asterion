package net.krodark.asterion.update.underworld.world;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** A bounded history in world coordinates. Moving the sampling window never moves a wake. */
public final class FerryWakeField {
    public static final int SIZE = 128;
    public static final double EXTENT = 64, LIFETIME = 240;
    private record Stamp(double x, double z, double dx, double dz, double time, double strength) { }
    private record Track(double x, double z, double time) { }
    private final ArrayDeque<Stamp> stamps = new ArrayDeque<>();
    private final Map<Integer, Track> presences = new HashMap<>();
    private final float[] foam = new float[SIZE * SIZE], height = new float[SIZE * SIZE];
    private double lastX = Double.NaN, lastZ, lastTime;
    public int originX, originZ;

    public void clear() { stamps.clear(); presences.clear(); lastX = Double.NaN; Arrays.fill(foam, 0); Arrays.fill(height, 0); }
    public int size() { return stamps.size(); }
    public void recordPresence(int id, double x, double z, double time, double strength) {
        Track previous = presences.put(id, new Track(x, z, time));
        if (previous == null) return;
        double elapsed = time - previous.time;
        double dx = x - previous.x, dz = z - previous.z, distance = Math.hypot(dx, dz);
        if (elapsed <= 0 || elapsed > 12 || distance < .24 || distance > 5) return;
        stamps.addLast(new Stamp(x, z, dx / distance, dz / distance, time,
                Math.clamp(strength * distance / elapsed * 3, 0, .75)));
        while (stamps.size() > 96) stamps.removeFirst();
    }
    public void record(double x, double z, double time, double immersion) {
        if (!Double.isFinite(lastX)) { lastX=x; lastZ=z; lastTime=time; return; }
        double dx=x-lastX, dz=z-lastZ, distance=Math.hypot(dx,dz), elapsed=time-lastTime;
        if (elapsed == 0) return; // Several render frames share one game tick: do not reset the trail anchor.
        if (elapsed<0 || distance>8 || elapsed>40) { lastX=x; lastZ=z; lastTime=time; return; }
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
        presences.entrySet().removeIf(e -> time - e.getValue().time > 40 || time < e.getValue().time);
        for(Stamp s:stamps) {
            double age=time-s.time, fade=Math.pow(1-age/LIFETIME,2)*s.strength;
            double birth=Math.clamp(age/6,0,1);
            fade*=birth*birth*(3-2*birth);
            double spread=.75+age*.010, width=.48+age*.0025;
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
    /** Small, pose-sized contact rings; rasterized into the existing field without another texture/pass. */
    public void addPresence(double x, double z, double halfWidth, double halfLength, double yaw, double time, double strength) {
        halfWidth=Math.clamp(halfWidth,.15,1.5); halfLength=Math.clamp(halfLength,.15,2);
        double reach=Math.max(halfWidth,halfLength)+1.5;
        int x0=Math.max(0,(int)Math.floor((x-originX-reach)*2)), x1=Math.min(SIZE-1,(int)Math.ceil((x-originX+reach)*2));
        int z0=Math.max(0,(int)Math.floor((z-originZ-reach)*2)), z1=Math.min(SIZE-1,(int)Math.ceil((z-originZ+reach)*2));
        double c=Math.cos(Math.toRadians(yaw)),s=Math.sin(Math.toRadians(yaw));
        for(int pz=z0;pz<=z1;pz++)for(int px=x0;px<=x1;px++) {
            double dx=originX+(px+.5)*.5-x,dz=originZ+(pz+.5)*.5-z;
            double lx=dx*c+dz*s,lz=-dx*s+dz*c;
            double distance=Math.sqrt(lx*lx/(halfWidth*halfWidth)+lz*lz/(halfLength*halfLength));
            double edge=(distance-1)*Math.min(halfWidth,halfLength);
            if(edge<-.1 || edge>1.5)continue;
            double ring=.5+.5*Math.cos(edge*9-time*.16);
            double value=Math.exp(-Math.max(0,edge)*2)*ring*Math.clamp(strength,0,1)*.35;
            int at=pz*SIZE+px;
            foam[at]=Math.max(foam[at],(float)value);
            height[at]+=(float)(.008*value);
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
