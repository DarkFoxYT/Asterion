package net.krodark.asterion.update.underworld.client;

import java.util.ArrayList;
import java.util.List;

/** Build only exposed drops, once alongside shoreline topology; no world queries per frame. */
public final class LimboCascadeMesh {
    public record Face(double x0, double z0, double x1, double z1, int upper, int lower, float nx, float nz,
                       float lipX0,float lipZ0,float lipX1,float lipZ1) {
        public Face(double x0,double z0,double x1,double z1,int upper,int lower,float nx,float nz) {
            this(x0,z0,x1,z1,upper,lower,nx,nz,0,0,0,0);
        }
    }
    private LimboCascadeMesh() { }
    public static List<Face> build(int x, int z, int[] heights, int stride, int margin) {
        var faces = new ArrayList<Face>();
        for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
            int index = (dz + margin) * stride + dx + margin;
            int upper = heights[index];
            if (upper == Integer.MIN_VALUE) continue;
            for (int side = 0; side < 4; side++) {
                int nx = side == 0 ? 1 : side == 1 ? -1 : 0;
                int nz = side == 2 ? 1 : side == 3 ? -1 : 0;
                int lower = heights[index + nx + nz * stride];
                if (lower == Integer.MIN_VALUE || upper - lower < 4) continue;
                int wx = x + dx, wz = z + dz;
                int x0 = wx + (nx > 0 ? 1 : 0), z0 = wz + (nz > 0 ? 1 : 0);
                faces.add(smooth(x0,z0,x0+(nx==0?1:0),z0+(nz==0?1:0),upper,lower,nx,nz));
            }
        }
        return List.copyOf(faces);
    }
    private static Face smooth(double x0,double z0,double x1,double z1,int upper,int lower,int nx,int nz) {
        var raw=new Face(x0,z0,x1,z1,upper,lower,nx,nz);
        int tier=1;
        while(tier<=4 && net.krodark.asterion.update.underworld.world.LimboCascades.waterYForTier(tier)!=lower)tier++;
        if(tier>4 || upper!=net.krodark.asterion.update.underworld.world.LimboCascades.waterYForTier(tier-1))return raw;
        double radius=net.krodark.asterion.update.underworld.world.LimboSeaRegions.SHORE_RADIUS-tier*3200;
        double cz=net.krodark.asterion.update.underworld.world.LimboSeaRegions.CENTER_Z;
        double r0=Math.hypot(x0,z0-cz),r1=Math.hypot(x1,z1-cz);
        // Smooth only genuine generated rims, never edited waterfalls or old chunk seams.
        if (Math.abs(r0-radius)>2 || Math.abs(r1-radius)>2) return raw;
        double rawX0=x0,rawZ0=z0,rawX1=x1,rawZ1=z1;
        x0*=radius/r0;z0=cz+(z0-cz)*radius/r0;
        x1*=radius/r1;z1=cz+(z1-cz)*radius/r1;
        double mx=(x0+x1)*.5,mz=(z0+z1)*.5-cz,r=Math.hypot(mx,mz);
        return new Face(x0,z0,x1,z1,upper,lower,(float)(-mx/r),(float)(-mz/r),
                (float)(rawX0-x0),(float)(rawZ0-z0),(float)(rawX1-x1),(float)(rawZ1-z1));
    }
}
