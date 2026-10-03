import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public final class PhysicsResourceSmoke {
    public static void main(String[] args) throws Exception {
        JsonObject animations=read("assets/asterion/geckolib/animations/entity/minotaur.animation.json").getAsJsonObject("animations");
        JsonObject right=animations.getAsJsonObject("axe_throw"), left=animations.getAsJsonObject("sword_throw_left");
        if(right.get("animation_length").getAsDouble()!=left.get("animation_length").getAsDouble())throw new AssertionError("Throw clip lengths differ");
        Set<String> bones=new HashSet<>();
        for(JsonElement geometry:read("assets/asterion/geckolib/models/entity/minotaur.geo.json").getAsJsonArray("minecraft:geometry"))
            for(JsonElement bone:geometry.getAsJsonObject().getAsJsonArray("bones"))bones.add(bone.getAsJsonObject().get("name").getAsString());
        for(var entry:right.getAsJsonObject("bones").entrySet()) {
            String mirror=entry.getKey().replace("right","TEMP").replace("left","right").replace("TEMP","left");
            JsonObject channels=left.getAsJsonObject("bones").getAsJsonObject(mirror);
            if(channels==null || !bones.contains(mirror))throw new AssertionError("Missing mirrored bone " + mirror);
            for(var channel:entry.getValue().getAsJsonObject().entrySet())for(var frame:channel.getValue().getAsJsonObject().entrySet()) {
                JsonObject mirrored=channels.getAsJsonObject(channel.getKey()).getAsJsonObject(frame.getKey());
                for(String phase:new String[]{"pre","post"})if(frame.getValue().getAsJsonObject().has(phase)) {
                    JsonArray a=frame.getValue().getAsJsonObject().getAsJsonArray(phase),b=mirrored.getAsJsonArray(phase);
                    for(int axis=0;axis<3;axis++) {
                        double sign=channel.getKey().equals("rotation") ? axis==0?1:-1 : channel.getKey().equals("position")&&axis==0?-1:1;
                        if(Math.abs(a.get(axis).getAsDouble()*sign-b.get(axis).getAsDouble())>1e-8)throw new AssertionError("Incorrect mirrored transform");
                    }
                }
            }
        }
        JsonObject recall=animations.getAsJsonObject("weapon_recall");
        if(recall.has("loop") && recall.get("loop").getAsBoolean())throw new AssertionError("Yank must play once");
        if(Math.abs(recall.get("animation_length").getAsDouble()-13/24.0)>1e-8)throw new AssertionError("Wrong grapple slice duration");
        for(var entry:recall.getAsJsonObject("bones").entrySet()) {
            if(!bones.contains(entry.getKey()))throw new AssertionError("Unknown recall bone");
        }
        for(String bone:new String[]{"rightshoulder","rightarm","lowerrightarm"}) {
            var rotation=recall.getAsJsonObject("bones").getAsJsonObject(bone).getAsJsonObject("rotation");
            var sourceFrame=animations.getAsJsonObject("chain_grapple").getAsJsonObject("bones").getAsJsonObject(bone)
                    .getAsJsonObject("rotation").get("1.25");
            var source=sourceFrame.isJsonArray()?sourceFrame.getAsJsonArray():sourceFrame.getAsJsonObject().getAsJsonArray("post");
            var first=rotation.getAsJsonObject("0.000000").getAsJsonArray("post");
            var mirror=recall.getAsJsonObject("bones").getAsJsonObject(bone.replace("right","left")).getAsJsonObject("rotation");
            for(int i=0;i<3;i++)if(Math.abs(source.get(i).getAsDouble()-first.get(i).getAsDouble())>1e-5)
                throw new AssertionError("Reel does not start at grapple frame 30");
            for(var frame:rotation.entrySet()) for(int i=0;i<3;i++)if(Math.abs(frame.getValue().getAsJsonObject().getAsJsonArray("post").get(i).getAsDouble()*(i==0?1:-1)
                    -mirror.getAsJsonObject(frame.getKey()).getAsJsonArray("post").get(i).getAsDouble())>1e-6)throw new AssertionError("Left yank differs");
        }
        var landing=animations.getAsJsonObject("asterion_leap_land");
        if(landing.get("animation_length").getAsDouble()!=1.4)throw new AssertionError("Wrong landing recovery");
        for(var bone:landing.getAsJsonObject("bones").entrySet())for(var channel:bone.getValue().getAsJsonObject().entrySet()) {
            var finalPose=channel.getValue().getAsJsonObject().getAsJsonObject("1.4").getAsJsonArray("post");
            for(var v:finalPose)if(v.getAsDouble()!=0)throw new AssertionError("Landing does not settle to standing");
        }
        var recipe=read("data/asterion/recipe/physics_chain.json");
        if(!recipe.getAsJsonObject("result").get("id").getAsString().equals("asterion:physics_chain"))throw new AssertionError("Wrong crafting result");
        read("assets/asterion/items/physics_chain.json");
        try(var stream=PhysicsResourceSmoke.class.getClassLoader().getResourceAsStream("assets/asterion/textures/block/mazesteel_chain.png")) {
            var texture=javax.imageio.ImageIO.read(stream);
            for(int x=4;x<=5;x++)for(int y=7;y<=8;y++)if((texture.getRGB(x,y)>>>24)!=255)throw new AssertionError("Transparent metal material pixel");
        }
        for(String lang:new String[]{"en_us","fr_fr"}) {
            var strings=read("assets/asterion/lang/"+lang+".json");
            for(String tip:new String[]{"chain","pillars","leap","axe","light","centipede"})
                if(!strings.has("tip.asterion.death."+tip) || strings.get("tip.asterion.death."+tip).getAsString().isBlank())
                    throw new AssertionError("Missing death-screen tip: "+lang+"/"+tip);
        }
        checkColliders();checkRing();checkRuins();checkWood();checkSwordArcs();
        var source=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/net/krodark/asterion/entity/MinotaurEntity.java"));
        var references=java.util.regex.Pattern.compile("then(?:Loop|PlayAndHold|Play)\\(\"([^\"]+)\"\\)").matcher(source);
        while(references.find())if(!animations.has(references.group(1)))throw new AssertionError("Missing requested animation "+references.group(1));
        for(String anchor:new String[]{"hand_chainL","hand_chainR"})if(!bones.contains(anchor))throw new AssertionError("Missing chain anchor "+anchor);
        System.out.println("PASS mirrored throws, grapple-frame yank on both arms, landing recovery, model colliders, closed phase-two ring, and chain resources");
    }
    private static void checkRuins() {
        for(long seed:new long[]{0,42,-7919,1234567}) {
            double inner=0,outer=0;
            for(int angle=0;angle<360;angle++) {
                double radians=Math.toRadians(angle);
                int x=(int)Math.round(Math.cos(radians)*65),z=(int)Math.round(Math.sin(radians)*65);
                inner+=net.krodark.asterion.worldgen.SunScorchedMaze.height(seed,x,z,48);
                x=(int)Math.round(Math.cos(radians)*99);z=(int)Math.round(Math.sin(radians)*99);
                outer+=net.krodark.asterion.worldgen.SunScorchedMaze.height(seed,x,z,48);
            }
            if(inner>=outer*.8)throw new AssertionError("Central walls are not more destroyed");
            for(int radius=62;radius<100;radius++) {
                int open=0;
                for(int angle=0;angle<360;angle++)if(!net.krodark.asterion.worldgen.SunScorchedMaze.wall(seed,(int)Math.round(Math.cos(Math.toRadians(angle))*radius),(int)Math.round(Math.sin(Math.toRadians(angle))*radius),16,3))open++;
                if(open<15)throw new AssertionError("Radial ruins seal a complete ring");
            }
        }
        System.out.println("PASS radial ruin entrances and increasing destruction toward the sun");
    }
    private static void checkWood() throws Exception {
        var model=read("assets/asterion/geckolib/models/block/shattered_dead_wood.geo.json");
        var cubes=model.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes");
        Set<Double> heights=new HashSet<>();
        for(var element:cubes) {
            var cube=element.getAsJsonObject();var origin=cube.getAsJsonArray("origin");var size=cube.getAsJsonArray("size");
            for(int i=0;i<3;i++) {
                double minimum=i==1?0:-8,maximum=i==1?16:8;
                double first=origin.get(i).getAsDouble(),last=first+size.get(i).getAsDouble();
                if(first<minimum || last>maximum)throw new AssertionError("Wood protrudes beyond its block");
            }
            heights.add(origin.get(1).getAsDouble()+size.get(1).getAsDouble());
        }
        if(heights.size()<3)throw new AssertionError("Wood crown is not shattered");
    }
    private static void checkColliders() {
        var center=net.minecraft.world.phys.Vec3.ZERO;
        for(String name:new String[]{"axe","sword","debris1","debris2","debris3","debris4","debris5","debris6","minotaur_door_debirs"}) {
            var boxes=net.krodark.asterion.physics.ModelCollider.load(name).world(center,new org.joml.Quaternionf().rotationXYZ(.3F,.8F,.5F),.7,center,0);
            var translation=new net.minecraft.world.phys.Vec3(1700.34,96.25,-1200.13);
            var moved=net.krodark.asterion.physics.ModelCollider.load(name).world(translation,new org.joml.Quaternionf().rotationXYZ(.3F,.8F,.5F),.7,center,0);
            for(int i=0;i<boxes.size();i++)if(!boxes.get(i).center().add(translation).equals(moved.get(i).center()))throw new AssertionError("Cached collider translation changes shape");
            if(boxes.isEmpty())throw new AssertionError("Empty model collider");
            for(var box:boxes) {
                if(!Double.isFinite(box.center().lengthSqr()))throw new AssertionError("Nonfinite collider");
                for(var axis:box.axes())if(Math.abs(axis.lengthSqr()-1)>1e-6)throw new AssertionError("Nonunit model axis");
                if(net.krodark.asterion.physics.ModelCollider.contact(box,box.bounds().inflate(.01))==null)
                    throw new AssertionError("Model cube missed its own bounds");
            }
        }
        var axe=net.krodark.asterion.physics.ModelCollider.load("axe").world(center,new org.joml.Quaternionf(),1,center,-Math.PI/2);
        var gap=new net.minecraft.world.phys.AABB(1.1,2,-.1,1.3,2.2,.1);
        for(var cube:axe)if(net.krodark.asterion.physics.ModelCollider.contact(cube,gap)!=null)throw new AssertionError("Empty space beside axe handle collides");
        if(axe.stream().noneMatch(net.krodark.asterion.physics.ModelCollider.Box::blade))throw new AssertionError("Axe head not classified");
        var bounds=net.krodark.asterion.physics.ModelCollider.bounds(axe);
        if(bounds.maxY<89.2/16.0-1e-5 || bounds.maxY>99/16.0+1e-5 || Math.abs(bounds.minY+6*Math.sqrt(2)/16)>1e-5)
            throw new AssertionError("Axe collider differs from model extents "+bounds);
    }
    private static void checkSwordArcs() {
        var origin=net.minecraft.world.phys.Vec3.ZERO;
        for(int side:new int[]{-1,1}) {
            double first=net.krodark.asterion.physics.SwordAttackMotion.angle(.7,side,0),last=first;
            for(int i=0;i<=100;i++) {
                double a=net.krodark.asterion.physics.SwordAttackMotion.angle(.7,side,i/100.0);
                if((a-last)*side< -1e-10 || Math.abs(a-.7)>Math.PI/4+1e-9)throw new AssertionError("Sword leaves front quarter arc");
                var rotation=net.krodark.asterion.physics.SwordAttackMotion.sweepRotation(a,side);
                var edge=rotation.transform(new org.joml.Vector3f(0,0,1));var normal=rotation.transform(new org.joml.Vector3f(1,0,0));
                if(Math.abs(normal.y)<.99999 || edge.dot((float)(-Math.sin(a)*side),0,(float)(Math.cos(a)*side))<.99999)throw new AssertionError("Flat blade does not lead with cutting edge");
                last=a;
            }
            if(Math.abs((last-first)*side-Math.PI/2)>1e-9)throw new AssertionError("Not a ninety-degree cut");
        }
        var flight=net.krodark.asterion.physics.SwordAttackMotion.flightRotation(new net.minecraft.world.phys.Vec3(0,.1,1));
        var tip=flight.transform(new org.joml.Vector3f(0,1,0));
        if(tip.dot(new net.minecraft.world.phys.Vec3(0,.1,1).normalize().toVector3f())<.9999)throw new AssertionError("Throw exposes flat face instead of blade tip");
        System.out.println("PASS opposite 90-degree arcs, horizontal blade planes, travel-aligned cutting edges, and edge-first throws");
    }
    private static void checkRing() {
        var center=new net.minecraft.world.phys.Vec3(.5,-30,.5);
        double progress=0,radius=50,start=.71;int steps=0;
        var first=net.krodark.asterion.physics.ArenaRingPath.point(center,radius,start);
        var previous=first;
        while(progress<Math.PI*2) {
            progress=net.krodark.asterion.physics.ArenaRingPath.advance(progress,1.85,radius);
            var next=net.krodark.asterion.physics.ArenaRingPath.point(center,radius,start+progress);
            if(Math.abs(next.distanceTo(center)-radius)>1e-9 || next.y!=center.y || next.distanceTo(previous)>1.851)
                throw new AssertionError("Ring drifts or jumps");
            previous=next;if(++steps>400)throw new AssertionError("Ring never finishes");
        }
        if(previous.distanceToSqr(first)>1e-10)throw new AssertionError("Ring does not close");
        if(net.krodark.asterion.physics.ArenaRingPath.allowed(false,0) || net.krodark.asterion.physics.ArenaRingPath.allowed(true,1)
                || !net.krodark.asterion.physics.ArenaRingPath.allowed(true,0))throw new AssertionError("Ring phase gate");
    }
    private static JsonObject read(String path) throws Exception {
        try(var stream=PhysicsResourceSmoke.class.getClassLoader().getResourceAsStream(path)) {
            if(stream==null)throw new AssertionError("Missing resource " + path);
            return JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
