import java.nio.file.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

/** Exercise the real dust shaders and reentrant chain work queue without launching a world. */
public final class DustShaderSmoke {
    public static void main(String[] args) throws Exception {
        checkQuality();
        checkQueue();
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(32,32,"Dust regression",0,0);
        if(window==0)throw new AssertionError("No graphics context");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            GL30.glBindVertexArray(GL30.glGenVertexArrays()); GL11.glViewport(0,0,32,32);
            int scene=texture(new float[]{.3f,.4f,.5f,1});
            int depth=texture(new float[]{.5f,.5f,.5f,1});
            int fast=program("atmosphere_fast"), volume=program("volume_integrate");
            for(float camera:new float[]{0,100,10000,-10000}) {
                float[] pixel=draw(fast,scene,depth,camera,1.5f);
                for(int c=0;c<3;c++)if(Math.abs(pixel[c]-(.3f+c*.1f))>.008f)
                    throw new AssertionError("Close surfaces fogged at world position "+camera+": "+Arrays.toString(pixel));
                pixel=draw(volume,scene,depth,camera,0);
                for(int c=0;c<4;c++)if(Math.abs(pixel[c]-(c==3?1:0))>.008f)
                    throw new AssertionError("Disabled volume is not transparent");
                pixel=draw(volume,scene,depth,camera,1.5f);
                for(float value:pixel)if(!Float.isFinite(value)||value<0||value>1.005f)
                    throw new AssertionError("Invalid dust scattering");
            }
            // Cross a noise-cell boundary: drifting dust must not pop between hash values.
            depth=texture(new float[]{1,1,1,1});
            float[] before=draw(fast,scene,depth,28.571f,1.5f);
            float[] after=draw(fast,scene,depth,28.572f,1.5f);
            for(int c=0;c<4;c++)if(Math.abs(before[c]-after[c])>.008f)
                throw new AssertionError("Dust jumps at noise boundary");
            System.out.println("PASS dust quality stable across governor changes, reentrant queue budget/order, GPU close-surface fog at large coordinates, transparent/finite volume and smooth noise");
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }
    private static void checkQuality() throws Exception {
        var config=net.krodark.asterion.AsterionConfig.INSTANCE;
        int saved=config.cinematicQuality;
        var field=net.krodark.asterion.client.PerformanceGovernor.class.getDeclaredField("quality");field.setAccessible(true);
        int savedGovernor=field.getInt(null);
        var method=net.krodark.asterion.client.render.post.AsterionPostEffects.class.getDeclaredMethod("dustQuality");method.setAccessible(true);
        try {
            for(int selected=0;selected<=2;selected++) {
                config.cinematicQuality=selected;
                for(int governor:new int[]{2,1,0,1,2,0}) {
                    field.setInt(null,governor);
                    if(((Number)method.invoke(null)).intValue()!=selected)throw new AssertionError("Dust changes with frame time");
                }
            }
        } finally {config.cinematicQuality=saved;field.setInt(null,savedGovernor);}
    }
    private static void checkQueue() throws Exception {
        var method=net.krodark.asterion.worldgen.GeneratedPhysicsChains.class.getDeclaredMethod("drainPending",Set.class,int.class);method.setAccessible(true);
        Set<Long> pending=new LinkedHashSet<>(List.of(1L,2L,3L));
        @SuppressWarnings("unchecked") List<Long> batch=(List<Long>)method.invoke(null,pending,2);
        for(long chunk:batch)pending.add(chunk+10); // synchronous CHUNK_LOAD during conversion
        if(!batch.equals(List.of(1L,2L)) || !new ArrayList<>(pending).equals(List.of(3L,11L,12L)))
            throw new AssertionError("Reentrant conversion loses work or exceeds budget");
    }
    private static float[] draw(int program,int scene,int depth,float camera,float density) {
        GL20.glUseProgram(program);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,scene);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"SceneSampler"),0);
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"DepthSampler"),1);
        List<Integer> buffers=new ArrayList<>();
        for(int block=0;block<GL20.glGetProgrami(program,GL31.GL_ACTIVE_UNIFORM_BLOCKS);block++) {
            String name=GL31.glGetActiveUniformBlockName(program,block);
            float[] data=new float[GL31.glGetActiveUniformBlocki(program,block,GL31.GL_UNIFORM_BLOCK_DATA_SIZE)/4];
            switch(name) {
                case "SamplerInfo" -> Arrays.fill(data,32);
                case "WorldData" -> {data[0]=data[5]=data[10]=data[15]=1;data[16]=camera;data[18]=camera;data[22]=1;}
                case "AtmosphereSettings" -> {data[0]=density;data[1]=1.5f;data[2]=1;}
                case "DustColor", "FogColor" -> {data[0]=.2f;data[1]=.12f;data[2]=.08f;}
                case "Intensity", "AsterionStrength" -> data[0]=1;
                case "AsterionQuality" -> data[0]=2;
            }
            int buffer=GL15.glGenBuffers();buffers.add(buffer);GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,buffer);GL15.glBufferData(GL31.GL_UNIFORM_BUFFER,data,GL15.GL_STATIC_DRAW);
            GL31.glUniformBlockBinding(program,block,block);GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,block,buffer);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        float[] pixel=new float[4];GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,pixel);
        for(int buffer:buffers)GL15.glDeleteBuffers(buffer);
        return pixel;
    }
    private static int texture(float[] pixels) {
        int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);return id;
    }
    private static int program(String fragment) throws Exception {
        int vs=compile(GL20.GL_VERTEX_SHADER,"#version 330\nout vec2 texCoord; void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2-1,0,1);texCoord=p;}");
        int fs=compile(GL20.GL_FRAGMENT_SHADER,Files.readString(Path.of("src/main/resources/assets/asterion/shaders/post/dimension/"+fragment+".fsh")));
        int program=GL20.glCreateProgram();GL20.glAttachShader(program,vs);GL20.glAttachShader(program,fs);GL20.glLinkProgram(program);
        if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(program));return program;
    }
    private static int compile(int type,String source) {
        int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(GL20.glGetShaderInfoLog(shader));return shader;
    }
}
