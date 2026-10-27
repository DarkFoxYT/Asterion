package net.krodark.asterion.physics;

import com.google.gson.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable compound collider built from the same Bedrock cubes as the rendered model. */
public final class ModelCollider {
    public record Box(Vec3 center, Vec3 half, Vec3[] axes, boolean blade) {
        public AABB bounds() {
            double x=radius(half,axes,WORLD[0]), y=radius(half,axes,WORLD[1]), z=radius(half,axes,WORLD[2]);
            return new AABB(center.x-x,center.y-y,center.z-z,center.x+x,center.y+y,center.z+z);
        }
    }
    public record Contact(Vec3 normal, double depth, Vec3 point, boolean blade) { }
    private static final Vec3[] WORLD={new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)};
    private static final Map<String,ModelCollider> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Box> boxes;
    private ModelCollider(List<Box> boxes) { this.boxes=List.copyOf(boxes); }
    public static ModelCollider load(String name) { return CACHE.computeIfAbsent(name,ModelCollider::read); }
    private static ModelCollider read(String name) {
        String path="/assets/asterion/geckolib/models/physics/"+name+".geo.json";
        try(var stream=Objects.requireNonNull(ModelCollider.class.getResourceAsStream(path),path);
            var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var bones=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry")
                    .get(0).getAsJsonObject().getAsJsonArray("bones");
            Map<String,JsonObject> definitions=new HashMap<>();
            for(var entry:bones) { var bone=entry.getAsJsonObject(); definitions.put(bone.get("name").getAsString(),bone); }
            Map<String,Matrix4f> transforms=new HashMap<>();
            List<Box> boxes=new ArrayList<>();
            for(var entry:bones) {
                var bone=entry.getAsJsonObject();
                if(!bone.has("cubes"))continue;
                String boneName=bone.get("name").getAsString();
                Matrix4f transform=boneTransform(boneName,definitions,transforms);
                for(var cubeEntry:bone.getAsJsonArray("cubes")) {
                    var cube=cubeEntry.getAsJsonObject();
                    Vec3 size=vec(cube,"size"), origin=vec(cube,"origin");
                    double inflate=cube.has("inflate")?cube.get("inflate").getAsDouble():0;
                    Vec3 half=size.scale(1/32.0).add(inflate/16,inflate/16,inflate/16);
                    if(size.z==0 && size.x>0 && size.y>0 && boneName.toLowerCase(Locale.ROOT).contains("blade")) {
                        Matrix4f planeMatrix=new Matrix4f(transform);rotateAt(planeMatrix,cube);
                        texturedBlade(boxes,name,cube,planeMatrix,origin,size);continue;
                    }
                    if(half.x<=0 || half.y<=0 || half.z<=0)continue;
                    Matrix4f matrix=new Matrix4f(transform);
                    rotateAt(matrix,cube);
                    Vec3 center=new Vec3(-(origin.x+size.x/2)/16,(origin.y+size.y/2)/16,(origin.z+size.z/2)/16);
                    center=position(matrix,center);
                    Vec3[] axes=new Vec3[3];
                    for(int i=0;i<3;i++)axes[i]=direction(matrix,WORLD[i]);
                    boxes.add(new Box(center,half,axes,boneName.toLowerCase(Locale.ROOT).contains("blade")));
                }
            }
            if(boxes.isEmpty())throw new IllegalStateException("Empty collider "+name);
            return new ModelCollider(boxes);
        }catch(Exception ex){throw new IllegalStateException("Cannot load model collider "+name,ex);}
    }
    private static void texturedBlade(List<Box> boxes,String name,JsonObject cube,Matrix4f matrix,Vec3 origin,Vec3 size) throws Exception {
        var uv=cube.getAsJsonObject("uv").getAsJsonObject("north");
        var start=uv.getAsJsonArray("uv");var span=uv.getAsJsonArray("uv_size");
        int width=Math.abs(span.get(0).getAsInt()),height=Math.abs(span.get(1).getAsInt());
        java.awt.image.BufferedImage image;
        try(var stream=Objects.requireNonNull(ModelCollider.class.getResourceAsStream("/assets/asterion/textures/physics/"+name+".png"))) {
            image=javax.imageio.ImageIO.read(stream);
        }
        Vec3[] axes={direction(matrix,WORLD[0]),direction(matrix,WORLD[1]),direction(matrix,WORLD[2])};
        // Merge matching horizontal runs vertically: exact opaque silhouette, bounded collider count.
        Map<Long,int[]> rectangles=new LinkedHashMap<>();List<int[]> finished=new ArrayList<>();
        for(int row=0;row<height;row++) {
            Set<Long> used=new HashSet<>();
            for(int col=0;col<width;) {
                if(!opaque(image,start,span,col,row)){col++;continue;}
                int left=col;while(col<width && opaque(image,start,span,col,row))col++;
                long key=((long)left<<32)|col;used.add(key);
                int[] rect=rectangles.get(key);
                if(rect==null)rectangles.put(key,new int[]{left,col,row,row+1});else rect[3]=row+1;
            }
            var iterator=rectangles.entrySet().iterator();while(iterator.hasNext()) {
                var entry=iterator.next();if(!used.contains(entry.getKey())){finished.add(entry.getValue());iterator.remove();}
            }
        }
        finished.addAll(rectangles.values());
        for(int[] rect:finished) {
            double x0=origin.x+size.x*rect[0]/width,x1=origin.x+size.x*rect[1]/width;
            double y0=origin.y+size.y*(1-rect[3]/(double)height),y1=origin.y+size.y*(1-rect[2]/(double)height);
            Vec3 center=position(matrix,new Vec3(-(x0+x1)/32,(y0+y1)/32,origin.z/16));
            boxes.add(new Box(center,new Vec3((x1-x0)/32,(y1-y0)/32,1/64.0),axes,true));
        }
    }
    private static boolean opaque(java.awt.image.BufferedImage image,JsonArray start,JsonArray span,int col,int row) {
        int x=start.get(0).getAsInt()+(span.get(0).getAsInt()<0?-col-1:col);
        int y=start.get(1).getAsInt()+(span.get(1).getAsInt()<0?-row-1:row);
        return x>=0 && y>=0 && x<image.getWidth() && y<image.getHeight() && (image.getRGB(x,y)>>>24)>=96;
    }
    private static Matrix4f boneTransform(String name, Map<String,JsonObject> bones, Map<String,Matrix4f> cache) {
        if(cache.containsKey(name))return cache.get(name);
        var bone=bones.get(name);
        Matrix4f matrix=bone.has("parent")?new Matrix4f(boneTransform(bone.get("parent").getAsString(),bones,cache)):new Matrix4f();
        rotateAt(matrix,bone); cache.put(name,matrix); return matrix;
    }
    private static void rotateAt(Matrix4f matrix, JsonObject object) {
        Vec3 p=vec(object,"pivot").multiply(-1,1,1).scale(1/16.0), r=vec(object,"rotation").scale(Math.PI/180);
        matrix.translate((float)p.x,(float)p.y,(float)p.z).rotateZ((float)r.z).rotateY((float)-r.y).rotateX((float)-r.x)
                .translate((float)-p.x,(float)-p.y,(float)-p.z);
    }
    private static Vec3 vec(JsonObject object,String key) {
        if(!object.has(key))return Vec3.ZERO;
        var a=object.getAsJsonArray(key); return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
    }
    private static Vec3 position(Matrix4f m,Vec3 v) {var p=m.transformPosition(new Vector3f((float)v.x,(float)v.y,(float)v.z));return new Vec3(p.x,p.y,p.z);}
    private static Vec3 direction(Matrix4f m,Vec3 v) {var p=m.transformDirection(new Vector3f((float)v.x,(float)v.y,(float)v.z));return new Vec3(p.x,p.y,p.z).normalize();}
    public List<Box> world(Vec3 center,Quaternionf rotation,double scale,Vec3 modelCenter, double alignment) {
        Matrix4f matrix=new Matrix4f().rotate(rotation)
                .scale((float)scale).translate((float)-modelCenter.x,(float)-modelCenter.y,(float)-modelCenter.z).rotateY((float)alignment);
        List<Box> result=new ArrayList<>(boxes.size());
        for(Box box:boxes) {
            Vec3[] axes=new Vec3[3];for(int i=0;i<3;i++)axes[i]=direction(matrix,box.axes[i]);
            result.add(new Box(position(matrix,box.center).add(center),box.half.scale(scale),axes,box.blade));
        }
        return result;
    }
    public static AABB bounds(List<Box> boxes) {
        AABB bounds=boxes.getFirst().bounds();for(int i=1;i<boxes.size();i++)bounds=bounds.minmax(boxes.get(i).bounds());return bounds;
    }
    public static Contact contact(Box box,AABB other) {
        Vec3 delta=other.getCenter().subtract(box.center), half=new Vec3(other.getXsize()/2,other.getYsize()/2,other.getZsize()/2);
        double depth=Double.POSITIVE_INFINITY;Vec3 normal=null;
        int i;
        for(i=0;i<15;i++) {
            Vec3 raw=i<3?box.axes[i]:i<6?WORLD[i-3]:box.axes[(i-6)/3].cross(WORLD[(i-6)%3]);
            if(raw.lengthSqr()<1e-10)continue;
            Vec3 axis=raw.normalize();double overlap=radius(box.half,box.axes,axis)+radius(half,WORLD,axis)-Math.abs(delta.dot(axis));
            if(overlap<=.00045)return null;
            if(overlap<depth){depth=overlap;normal=axis.scale(delta.dot(axis)<0?1:-1);}
        }
        Vec3 point=box.center;double[] extents={box.half.x,box.half.y,box.half.z};
        for(i=0;i<3;i++){double dot=box.axes[i].dot(normal);if(Math.abs(dot)>.001)point=point.add(box.axes[i].scale(-Math.signum(dot)*extents[i]));}
        point=new Vec3(Math.clamp(point.x,other.minX,other.maxX),Math.clamp(point.y,other.minY,other.maxY),Math.clamp(point.z,other.minZ,other.maxZ));
        return new Contact(normal,depth,point,box.blade);
    }
    private static double radius(Vec3 h,Vec3[] axes,Vec3 dir){return h.x*Math.abs(axes[0].dot(dir))+h.y*Math.abs(axes[1].dot(dir))+h.z*Math.abs(axes[2].dot(dir));}
}
