import net.krodark.asterion.update.underworld.world.FerryHull;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.*;
import java.nio.file.*;

public final class FerryHullSmoke {
    public static void check() throws Exception {
        double flat = 0, downhill = 0, uphill = 0;
        for (int tick = 0; tick < 120; tick++) {
            flat = net.krodark.asterion.update.underworld.world.FerryMotion.advance(flat, 1, true, true, 0);
            downhill = net.krodark.asterion.update.underworld.world.FerryMotion.advance(downhill, 1, true, true, .25);
            uphill = net.krodark.asterion.update.underworld.world.FerryMotion.advance(uphill, 1, true, true, -.25);
        }
        if (downhill < flat + .015 || uphill > flat - .015)
            throw new AssertionError("Wave slopes must change forward speed");
        double coasting = flat;
        for (int tick = 0; tick < 30; tick++)
            coasting = net.krodark.asterion.update.underworld.world.FerryMotion.advance(coasting, 0, true, true, 0);
        if (coasting <= flat * .65 || coasting >= flat)
            throw new AssertionError("Ferry coasting lost momentum or failed to slow");
        if (FerryHull.DECK >= 21.0 / 16.0 || FerryHull.RIDER_DROP < .2)
            throw new AssertionError("Player stance or riding seat was not lowered");
        System.out.println("PASS downhill momentum, uphill drag, coasting inertia, and lower player stance/seat.");
        var seat=new net.krodark.asterion.update.underworld.FerryJourneyState.Seat(.35,2.6,47,true);
        var json=net.krodark.asterion.update.underworld.FerryJourneyState.Seat.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,seat).getOrThrow();
        var restored=net.krodark.asterion.update.underworld.FerryJourneyState.Seat.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
        if(!restored.equals(seat))throw new AssertionError("Relog deck coordinates or paid fare lost on save");
        for(float yaw:new float[]{0,47,179,270})for(float pitch:new float[]{-12,0,12})for(float roll:new float[]{-10,0,10}) {
            Vec3 local=new Vec3(.35,FerryHull.DECK,2.6);
            Vec3 world=FerryHull.world(local,yaw,pitch,roll);
            if(FerryHull.local(world,yaw,pitch,roll).distanceTo(local)>1e-8)throw new AssertionError("Deck frame roundtrip");
        }
        // Standing feet must retain their local deck position through sustained turns and rocking.
        Vec3 feet = new Vec3(.35, FerryHull.DECK, 2.6);
        Vec3 carried = FerryHull.world(feet, 0, 0, 0);
        float previousYaw = 0, previousPitch = 0, previousRoll = 0;
        for (int tick = 1; tick <= 1200; tick++) {
            Vec3 local = FerryHull.local(carried, previousYaw, previousPitch, previousRoll);
            float yaw = tick * .3F, pitch = (float)Math.sin(tick * .03) * 10, roll = (float)Math.cos(tick * .02) * 8;
            carried = FerryHull.world(new Vec3(local.x, FerryHull.DECK, local.z), yaw, pitch, roll);
            if (FerryHull.local(carried, yaw, pitch, roll).distanceTo(feet) > 1e-7)
                throw new AssertionError("Walking deck carry drifts during rocking");
            previousYaw = yaw; previousPitch = pitch; previousRoll = roll;
        }
        System.out.println("PASS 1200 ticks of deck-local carry without drift while turning, pitching and rolling");
        Vec3 clipped=FerryHull.constrain(0,0,8,4,.25);
        if(FerryHull.deckDistance(clipped.x,clipped.z)>-.249)throw new AssertionError("Rail collision escaped hull");
        if(FerryHull.deckDistance(0,3.2)>0 || FerryHull.deckDistance(1.8,0)<0)throw new AssertionError("Deck footprint disagrees with model");
        int shader=GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(shader,"#version 330\nuniform vec3 Point;out float distanceValue;\n"+
                Files.readString(Path.of("src/main/resources/assets/asterion/shaders/include/limbo_hull.glsl"))+
                "\nvoid main(){distanceValue=hullDistance(Point.xzy);gl_Position=vec4(0);}");
        GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(GL20.glGetShaderInfoLog(shader));
        int program=GL20.glCreateProgram();GL20.glAttachShader(program,shader);
        GL30.glTransformFeedbackVaryings(program,new String[]{"distanceValue"},GL30.GL_INTERLEAVED_ATTRIBS);GL20.glLinkProgram(program);
        if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(program));
        int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);GL20.glUseProgram(program);
        int buffer=GL15.glGenBuffers();GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,buffer);
        GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,4L,GL15.GL_STREAM_READ);GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,buffer);
        GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);int uniform=GL20.glGetUniformLocation(program,"Point");
        for(float y:new float[]{.2F,.5F,.7F,1.3F})for(float z=-4;z<=5;z+=.5F)for(float x=-2;x<=2;x+=.5F) {
            GL20.glUniform3f(uniform,x,y,z);GL30.glBeginTransformFeedback(GL11.GL_POINTS);GL11.glDrawArrays(GL11.GL_POINTS,0,1);GL30.glEndTransformFeedback();
            float[] result=new float[1];GL15.glGetBufferSubData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,result);
            if(Math.abs(result[0]-FerryHull.hullDistance(x,y,z))>1e-5)throw new AssertionError("Water mask and physical hull disagree");
        }
        GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);GL15.glDeleteBuffers(buffer);GL30.glDeleteVertexArrays(vao);
        GL20.glUseProgram(0);GL20.glDeleteProgram(program);GL20.glDeleteShader(shader);
        System.out.println("PASS CPU/GPU hull profile, swept deck collisions, deck transforms and relog seat/fare serialization.");
    }
}
