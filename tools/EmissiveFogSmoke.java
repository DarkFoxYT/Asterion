import java.nio.file.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

/** Render the real emissive fragment shader under red fog; verify opacity, texture colour and black masks. */
public final class EmissiveFogSmoke {
    public static void main(String[] args) throws Exception {
        if(!GLFW.glfwInit())throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(32,32,"Emissive fog check",0,0);
        if(window==0)throw new AssertionError("No graphics context");
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            String vertex="#version 330 core\nuniform float Distance; out float sphericalVertexDistance; out float cylindricalVertexDistance; out vec4 vertexColor; out vec2 texCoord0; void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2-1,0,1);texCoord0=p;sphericalVertexDistance=Distance;cylindricalVertexDistance=Distance;vertexColor=vec4(1);}";
            int vs=compile(GL20.GL_VERTEX_SHADER,vertex),fs=compile(GL20.GL_FRAGMENT_SHADER,ShaderIncludes.resolve(Files.readString(Path.of("src/main/resources/assets/asterion/shaders/core/enhanced_emissive.fsh"))));
            int program=GL20.glCreateProgram();GL20.glAttachShader(program,vs);GL20.glAttachShader(program,fs);GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(program));
            GL20.glUseProgram(program);int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
            List<Integer> buffers=new ArrayList<>();
            for(int block=0;block<GL20.glGetProgrami(program,GL31.GL_ACTIVE_UNIFORM_BLOCKS);block++) {
                String name=GL31.glGetActiveUniformBlockName(program,block);
                float[] data=new float[GL31.glGetActiveUniformBlocki(program,block,GL31.GL_UNIFORM_BLOCK_DATA_SIZE)/4];
                if(name.equals("Fog")){data[0]=1;data[3]=1;data[4]=10;data[5]=20;data[6]=10;data[7]=20;}
                if(name.equals("DynamicTransforms"))Arrays.fill(data,16,20,1);
                int buffer=GL15.glGenBuffers();buffers.add(buffer);GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,buffer);GL15.glBufferData(GL31.GL_UNIFORM_BUFFER,data,GL15.GL_STATIC_DRAW);
                GL31.glUniformBlockBinding(program,block,block);GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,block,buffer);
            }
            int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glViewport(0,0,32,32);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            float[] background={.10F,.20F,.40F},pixel=new float[4];
            for(float[] tint:new float[][]{{1,1,1,1},{.2F,.7F,1,1},{0,0,0,1}}) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,tint);
                for(float distance:new float[]{0,15,30}) {
                    GL11.glClearColor(background[0],background[1],background[2],1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                    GL20.glUniform1f(GL20.glGetUniformLocation(program,"Distance"),distance);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
                    GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,pixel);
                    float alpha=tint[0]+tint[1]+tint[2]==0?0:distance==0?1:distance==15?.5F:0;
                    for(int channel=0;channel<3;channel++) {
                        float expected=tint[channel]*alpha+background[channel]*(1-alpha);
                        if(Math.abs(pixel[channel]-expected)>.012)throw new AssertionError("Opaque/coloured fog regression: distance="+distance+", channel="+channel+", actual="+pixel[channel]+", expected="+expected);
                    }
                }
            }
            GL11.glDisable(GL11.GL_BLEND);
            int blur=compile(GL20.GL_FRAGMENT_SHADER,Files.readString(Path.of("src/main/resources/assets/asterion/shaders/post/dimension/dead_sun_bloom_combined.fsh")));int blurVertex=compile(GL20.GL_VERTEX_SHADER,vertex.replace("texCoord0","texCoord"));
            int blurProgram=GL20.glCreateProgram();GL20.glAttachShader(blurProgram,blurVertex);GL20.glAttachShader(blurProgram,blur);GL20.glLinkProgram(blurProgram);
            if(GL20.glGetProgrami(blurProgram,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(blurProgram));
            GL20.glUseProgram(blurProgram);
            for(int block=0;block<GL20.glGetProgrami(blurProgram,GL31.GL_ACTIVE_UNIFORM_BLOCKS);block++) {
                String name=GL31.glGetActiveUniformBlockName(blurProgram,block);
                float[] data=new float[GL31.glGetActiveUniformBlocki(blurProgram,block,GL31.GL_UNIFORM_BLOCK_DATA_SIZE)/4];
                if(name.equals("SamplerInfo"))Arrays.fill(data,32);else if(name.equals("AsterionQuality"))data[0]=2;
                int buffer=GL15.glGenBuffers();buffers.add(buffer);GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,buffer);GL15.glBufferData(GL31.GL_UNIFORM_BUFFER,data,GL15.GL_STATIC_DRAW);
                GL31.glUniformBlockBinding(blurProgram,block,block);GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,block,buffer);
            }
            for(float[] tint:new float[][]{{1,1,1,1},{.2F,.7F,1,1}}) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,tint);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
                GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,pixel);
                for(int channel=0;channel<3;channel++)if(Math.abs(pixel[channel]-tint[channel])>.012)throw new AssertionError("Bloom changes source colour/brightness");
            }
            GL20.glDeleteProgram(blurProgram);GL20.glDeleteShader(blurVertex);GL20.glDeleteShader(blur);
            var chain=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/asterion/post_effect/dimension/dead_sun.json")));
            net.minecraft.client.renderer.PostChainConfig.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,chain).getOrThrow();
            if(chain.getAsJsonObject().getAsJsonArray("passes").size()!=4)throw new AssertionError("Duplicate sun blur pass");
            for(int buffer:buffers)GL15.glDeleteBuffers(buffer);GL11.glDeleteTextures(texture);GL30.glDeleteVertexArrays(vao);GL20.glDeleteProgram(program);GL20.glDeleteShader(vs);GL20.glDeleteShader(fs);
            System.out.println("PASS real GPU: white/original texture colour, transparent fog fade under red fog, black mask transparency, combined blur shader, four-pass sun chain");
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static int compile(int type,String source){int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(GL20.glGetShaderInfoLog(shader));return shader;}
}
