package net.krodark.asterion.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Flat, double-sided pixel sprites laid out at a fixed distance along the chain. */
public final class ChainGeometry {
    private static final double LINK_SPACING = .25;
    private static final Vec3 WIDTH_REFERENCE = new Vec3(1,0,1).normalize();
    private static final Vec3 WIDTH_FALLBACK = new Vec3(1,0,-1).normalize();
    private static final ThreadLocal<org.joml.Vector3f[]> QUAD = ThreadLocal.withInitial(() ->
            new org.joml.Vector3f[]{new org.joml.Vector3f(),new org.joml.Vector3f(),
                    new org.joml.Vector3f(),new org.joml.Vector3f(),new org.joml.Vector3f(),new org.joml.Vector3f()});
    private ChainGeometry() { }
    public static void draw(PoseStack.Pose pose, VertexConsumer out, Vec3[] points, Vec3 offset, Vec3 camera, int light) {
        draw(pose,out,points,offset,camera,light,LINK_SPACING,.5);
    }
    public static void drawHanging(PoseStack.Pose pose, VertexConsumer out, Vec3[] points, Vec3 offset, Vec3 camera, int light) {
        drawCrossed(pose,out,points,offset,light,1,1);
    }
    public static void drawWeapon(PoseStack.Pose pose,VertexConsumer out,Vec3[] points,Vec3 offset,int light) {
        drawCrossed(pose,out,points,offset,light,.25,.25);
    }
    private static void drawCrossed(PoseStack.Pose pose,VertexConsumer out,Vec3[] points,Vec3 offset,int light,double spacing,double width) {
        if(points==null || points.length<2)return;
        double[] arc=new double[points.length];
        for(int i=1;i<points.length;i++)arc[i]=arc[i-1]+points[i-1].distanceTo(points[i]);
        double length=arc[arc.length-1];if(!Double.isFinite(length) || length<.001)return;
        // Adjacent tiles share their boundary. Keep its unflipped frame for the next tile.
        Vec3 top=sample(points,arc,0).add(offset);
        Vec3 topAxis=tangent(points,arc,0,length),topWidth=fixedWidth(topAxis);
        Vec3 topCross=topAxis.cross(topWidth).normalize();
        int tiles=Math.min(288,(int)Math.ceil(length/spacing));
        for(int tile=0;tile<tiles;tile++) {
            double from=tile*spacing,to=Math.min(length,from+spacing);
            Vec3 bottom=sample(points,arc,to).add(offset);
            Vec3 bottomAxis=tangent(points,arc,to,length),bottomWidth=fixedWidth(bottomAxis);
            Vec3 bottomCross=bottomAxis.cross(bottomWidth).normalize();
            for(int plane=0;plane<2;plane++) {
                Vec3 a=plane==0?topWidth:topCross;
                Vec3 b=plane==0?bottomWidth:bottomCross;
                if(a.dot(b)<0)b=b.scale(-1);
                Vec3 leftTop=top.subtract(a.scale(width*.5)),rightTop=top.add(a.scale(width*.5));
                Vec3 leftBottom=bottom.subtract(b.scale(width*.5)),rightBottom=bottom.add(b.scale(width*.5));
                Vec3 normal=a.cross(topAxis).normalize();
                // All four tile variants match the front/back faces in the mazesteel block model.
                float u=plane==0?0:4.25F/16F,vBack=4.25F/16F,height=(float)((to-from)/spacing*.25);
                quad(pose,out,leftTop,rightTop,rightBottom,leftBottom,normal,light,u,vBack,height);
            }
            top=bottom; topAxis=bottomAxis; topWidth=bottomWidth; topCross=bottomCross;
        }
    }
    private static Vec3 tangent(Vec3[] points,double[] arc,double at,double length) {
        Vec3 axis=sample(points,arc,Math.min(length,at+.1)).subtract(sample(points,arc,Math.max(0,at-.1)));
        return axis.lengthSqr()<1e-10?new Vec3(0,-1,0):axis.normalize();
    }
    private static void quad(PoseStack.Pose pose,VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,
                             Vec3 normal,int light,float u,float backV,float height) {
        var scratch=QUAD.get();
        // Front/back share four positions and one normal each; transform those just once.
        pose.pose().transformPosition((float)a.x,(float)a.y,(float)a.z,scratch[0]);
        pose.pose().transformPosition((float)b.x,(float)b.y,(float)b.z,scratch[1]);
        pose.pose().transformPosition((float)c.x,(float)c.y,(float)c.z,scratch[2]);
        pose.pose().transformPosition((float)d.x,(float)d.y,(float)d.z,scratch[3]);
        pose.transformNormal((float)normal.x,(float)normal.y,(float)normal.z,scratch[4]);
        pose.transformNormal((float)-normal.x,(float)-normal.y,(float)-normal.z,scratch[5]);
        vertex(out,scratch[0],scratch[4],light,u,0);
        vertex(out,scratch[1],scratch[4],light,u+.25F,0);
        vertex(out,scratch[2],scratch[4],light,u+.25F,height);
        vertex(out,scratch[3],scratch[4],light,u,height);
        vertex(out,scratch[3],scratch[5],light,u,backV+height);
        vertex(out,scratch[2],scratch[5],light,u+.25F,backV+height);
        vertex(out,scratch[1],scratch[5],light,u+.25F,backV);
        vertex(out,scratch[0],scratch[5],light,u,backV);
    }
    private static void vertex(VertexConsumer out,org.joml.Vector3f v,org.joml.Vector3f n,
                               int light,float u,float w) {
        out.addVertex(v.x,v.y,v.z,-1,u,w,OverlayTexture.NO_OVERLAY,light,n.x,n.y,n.z);
    }
    private static Vec3 fixedWidth(Vec3 axis) {
        Vec3 reference=WIDTH_REFERENCE;
        Vec3 width=reference.subtract(axis.scale(reference.dot(axis)));
        if(width.lengthSqr()<1e-6){reference=WIDTH_FALLBACK;width=reference.subtract(axis.scale(reference.dot(axis)));}
        return width.normalize();
    }
    private static void draw(PoseStack.Pose pose, VertexConsumer out, Vec3[] points, Vec3 offset, Vec3 camera, int light,double spacing,double size) {
        if(points==null || points.length<2)return;
        double[] arc = new double[points.length];
        for (int i=1;i<points.length;i++) arc[i] = arc[i-1] + points[i-1].distanceTo(points[i]);
        double length = arc[arc.length-1];
        if (length < .001 || !Double.isFinite(length)) return;
        // Anchored spacing prevents small length changes from shuffling every link.
        Vec3 previousWidth = null;
        int links = Math.min(288, (int)Math.ceil(length / spacing));
        for (int link=0;link<links;link++) {
            double tileLength=Math.min(spacing,length-link*spacing);
            double at = spacing==1 ? link*spacing+tileLength*.5 : Math.min(length, (link+.5) * spacing);
            Vec3 center = sample(points, arc, at).add(offset);
            Vec3 axis = sample(points, arc, Math.min(length, at+.1))
                    .subtract(sample(points, arc, Math.max(0, at-.1))).normalize();
            if (axis.lengthSqr() < .000001) continue;
            Vec3 width = axis.cross(camera.subtract(center));
            if (width.lengthSqr() < .000001) {
                Vec3 reference = previousWidth == null ? new Vec3(1,0,0) : previousWidth;
                width = reference.subtract(axis.scale(reference.dot(axis)));
                if (width.lengthSqr() < .000001) width = new Vec3(0,0,1).cross(axis);
            }
            width = width.normalize();
            if (previousWidth != null && width.dot(previousWidth) < 0) width = width.scale(-1);
            previousWidth = width;
            Vec3 normal = width.cross(axis).normalize();
            Vec3 across = width.scale(size*.5), along = axis.scale(size*.5);
            if(spacing==1)along=along.scale(-tileLength);
            float bottomUv=spacing==1?(float)(.25*tileLength):.25F;
            Vec3 a=center.subtract(across).add(along), b=center.add(across).add(along);
            Vec3 c=center.add(across).subtract(along), d=center.subtract(across).subtract(along);
            // The full 16x16 sprite includes the transparent hole; no extruded bars.
            vertex(pose,out,a,normal,light,0,0); vertex(pose,out,b,normal,light,.25F,0);
            vertex(pose,out,c,normal,light,.25F,bottomUv); vertex(pose,out,d,normal,light,0,bottomUv);
            normal=normal.scale(-1);
            vertex(pose,out,d,normal,light,0,bottomUv); vertex(pose,out,c,normal,light,.25F,bottomUv);
            vertex(pose,out,b,normal,light,.25F,0); vertex(pose,out,a,normal,light,0,0);
        }
    }
    private static Vec3 sample(Vec3[] points, double[] arc, double distance) {
        int low=1,high=points.length-1;
        while(low<high) {int mid=(low+high)>>>1;if(arc[mid]<distance)low=mid+1;else high=mid;}
        int i=low;
        if(arc[i]>=distance) {
            double span=arc[i]-arc[i-1];
            return span < .000001 ? points[i] : points[i-1].lerp(points[i], (distance-arc[i-1])/span);
        }
        return points[points.length-1];
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Vec3 v, Vec3 n, int light, float u, float w) {
        out.addVertex(pose, (float)v.x, (float)v.y, (float)v.z).setColor(-1)
                .setUv(u,w).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, (float)n.x, (float)n.y, (float)n.z);
    }
}
