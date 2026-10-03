package net.krodark.asterion.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashSet;
import java.util.Set;

/** A continuous voxel tube following a slack curve between two wall anchors. */
public final class HangingLeafSpan {
    private HangingLeafSpan() { }
    public static Vec3 point(Vec3 start,Vec3 end,double t,double sag) {
        return start.lerp(end,t).add(0,-4*sag*t*(1-t),0);
    }
    public static Set<BlockPos> blocks(BlockPos start,BlockPos end,int thickness,double sag) {
        if(thickness<3 || thickness>5)throw new IllegalArgumentException("Leaf vine thickness must be 3–5");
        Vec3 a=Vec3.atCenterOf(start),b=Vec3.atCenterOf(end);
        int steps=(int)Math.ceil((a.distanceTo(b)+sag*2)*2);
        if(steps>160)throw new IllegalArgumentException("Leaf span too long");
        Set<BlockPos> blocks=new LinkedHashSet<>();
        for(int step=0;step<=steps;step++) {
            double t=step/(double)Math.max(1,steps);
            Vec3 center=point(a,b,t,sag);
            // Strong central growth tapers smoothly into the stone at each attachment.
            double radius=1.25+(thickness*.5-.05-1.25)*Math.sin(Math.PI*t);
            BlockPos cell=BlockPos.containing(center);
            for(int x=-3;x<=3;x++)for(int y=-3;y<=3;y++)for(int z=-3;z<=3;z++) {
                BlockPos pos=cell.offset(x,y,z);
                if(Vec3.atCenterOf(pos).distanceToSqr(center)<=radius*radius)blocks.add(pos);
            }
        }
        return blocks;
    }
}
