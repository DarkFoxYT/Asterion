import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** Render the actual material/texture for all seas using a hidden OpenGL context. */
public final class CascadeVisualSmoke {
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW unavailable");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(640,416,"Cascade material check",0,0);
        if (window==0) throw new AssertionError("No driver context");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            int vertex=compile("vsh",GL20.GL_VERTEX_SHADER), fragment=compile("fsh",GL20.GL_FRAGMENT_SHADER);
            int program=GL20.glCreateProgram();
            GL20.glAttachShader(program,vertex); GL20.glAttachShader(program,fragment); GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0) throw new AssertionError(GL20.glGetProgramInfoLog(program));
            GL20.glUseProgram(program);
            var uniforms=new java.util.ArrayList<Integer>();
            var uniformNames=new java.util.HashMap<String,Integer>();
            for(int block=0;block<GL20.glGetProgrami(program,GL31.GL_ACTIVE_UNIFORM_BLOCKS);block++) {
                String name=GL31.glGetActiveUniformBlockName(program,block);
                float[] data=new float[GL31.glGetActiveUniformBlocki(program,block,GL31.GL_UNIFORM_BLOCK_DATA_SIZE)/4];
                if (name.equals("Projection")) new Matrix4f().ortho(-20,20,-4,22,.1F,128).get(data);
                else if (name.equals("DynamicTransforms")) {
                    new Matrix4f().lookAt(0,0,36,0,0,0,0,1,0).get(data);
                    java.util.Arrays.fill(data,16,20,1);
                } else if (name.equals("Fog")) {
                    data[3]=1; data[4]=80; data[5]=160; data[6]=160; data[7]=240; data[8]=240; data[9]=240;
                }
                int buffer=GL15.glGenBuffers(); uniforms.add(buffer);
                uniformNames.put(name,buffer);
                GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,buffer); GL15.glBufferData(GL31.GL_UNIFORM_BUFFER,data,GL15.GL_STATIC_DRAW);
                GL31.glUniformBlockBinding(program,block,block); GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,block,buffer);
            }
            BufferedImage water;
            try(var stream=CascadeVisualSmoke.class.getClassLoader().getResourceAsStream("assets/minecraft/textures/block/water_still.png")) {
                water=ImageIO.read(stream);
            }
            ByteBuffer pixels=MemoryUtil.memAlloc(water.getWidth()*water.getHeight()*4);
            for(int y=0;y<water.getHeight();y++)for(int x=0;x<water.getWidth();x++) {
                int c=water.getRGB(x,y); pixels.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>24));
            }
            pixels.flip(); int texture=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,water.getWidth(),water.getHeight(),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            MemoryUtil.memFree(pixels);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"Sampler0"),0);
            int vao=GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao);
            int buffer=GL15.glGenBuffers(); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);
            String[] names={"styx","phlegethon","lethe","acheron","cocytus","styx-profile"};
            Files.createDirectories(Path.of("build/reports/cascades"));
            long[] hashes=new long[6];
            for(int sea=0;sea<6;sea++) {
                if(sea==5) {
                    GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,uniformNames.get("DynamicTransforms"));
                    GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER,0,new Matrix4f().lookAt(24,16,36,0,9,0,0,1,0).get(new float[16]));
                    GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,uniformNames.get("Projection"));
                    GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER,0,new Matrix4f().ortho(-23,23,-13,13,.1F,128).get(new float[16]));
                }
                float[] mesh=new float[32*12*6*12*2]; int at=0;
                for(int shell=0;shell<2;shell++)for(int x=0;x<32;x++)for(int band=0;band<12;band++)for(int corner:new int[]{0,1,2,0,2,3}) {
                    float u=(band+(corner>=2?1:0))/12F,t=u*u;
                    float px=x+(corner==1||corner==2?1:0)-16;
                    mesh[at++]=px; mesh[at++]=18*(1-t); mesh[at++]=0;
                    mesh[at++]=px; mesh[at++]=((sea==5?0:sea)+.5F)*3200+58;
                    mesh[at++]=t; mesh[at++]=.5F; mesh[at++]=shell; mesh[at++]=1;
                    mesh[at++]=0; mesh[at++]=0; mesh[at++]=1;
                }
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER,mesh,GL15.GL_STATIC_DRAW);
                attribute(program,"Position",3,0); attribute(program,"UV0",2,12);
                attribute(program,"Color",4,20); attribute(program,"Normal",3,36);
                GL30.glVertexAttribI2i(GL20.glGetAttribLocation(program,"UV1"),47,29);
                GL30.glVertexAttribI2i(GL20.glGetAttribLocation(program,"UV2"),1234,0);
                GL11.glViewport(0,0,640,416); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_BLEND);
                GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(true);
                GL11.glClearColor(.03F,.035F,.04F,1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
                GL11.glDrawArrays(GL11.GL_TRIANGLES,0,mesh.length/12);
                if(sea==0 || sea==5) {
                    GL11.glFinish();
                    renderMist(uniformNames);
                    GL20.glUseProgram(program);GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);
                    for(int block=0;block<GL20.glGetProgrami(program,GL31.GL_ACTIVE_UNIFORM_BLOCKS);block++)
                        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,block,uniformNames.get(GL31.glGetActiveUniformBlockName(program,block)));
                    attribute(program,"Position",3,0);attribute(program,"UV0",2,12);
                    attribute(program,"Color",4,20);attribute(program,"Normal",3,36);
                    GL30.glVertexAttribI2i(GL20.glGetAttribLocation(program,"UV1"),47,29);
                    GL30.glVertexAttribI2i(GL20.glGetAttribLocation(program,"UV2"),1234,0);
                }
                pixels=MemoryUtil.memAlloc(640*416*4); GL11.glReadPixels(0,0,640,416,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                var image=new BufferedImage(640,416,BufferedImage.TYPE_INT_RGB);
                int highlights=0; long hash=1;
                for(int y=0;y<416;y++)for(int x=0;x<640;x++) {
                    int i=(y*640+x)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;
                    int rgb=(r<<16)|(g<<8)|b; image.setRGB(x,415-y,rgb); hash=hash*31+rgb;
                    if(r>95&&g>95&&b>95) highlights++;
                }
                MemoryUtil.memFree(pixels); hashes[sea]=hash;
                if(highlights<500) throw new AssertionError("Missing bright foam: "+names[sea]);
                ImageIO.write(image,"png",Path.of("build/reports/cascades/"+names[sea]+".png").toFile());
                System.out.println("PASS actual "+names[sea]+" waterfall material: "+highlights+" bright foam pixels");
            }
            for(int i=0;i<5;i++)for(int j=i+1;j<5;j++)if(hashes[i]==hashes[j])throw new AssertionError("Sea styles are identical");
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Driver render error");
            GL20.glUseProgram(0); GL20.glDeleteProgram(program); GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment);
            uniforms.forEach(GL15::glDeleteBuffers); GL15.glDeleteBuffers(buffer); GL30.glDeleteVertexArrays(vao); GL11.glDeleteTextures(texture);
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }
    private static void attribute(int program,String name,int size,long offset) {
        int index=GL20.glGetAttribLocation(program,name); GL20.glEnableVertexAttribArray(index);
        GL20.glVertexAttribPointer(index,size,GL11.GL_FLOAT,false,48,offset);
    }
    private static int compile(String extension,int type) throws Exception {
        return compile("limbo_cascade",extension,type);
    }
    private static void renderMist(java.util.Map<String,Integer> uniformNames) throws Exception {
        int vs=compile("limbo_cascade_mist","vsh",GL20.GL_VERTEX_SHADER),fs=compile("limbo_cascade_mist","fsh",GL20.GL_FRAGMENT_SHADER);
        int program=GL20.glCreateProgram();GL20.glAttachShader(program,vs);GL20.glAttachShader(program,fs);GL20.glLinkProgram(program);
        if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(program));
        GL20.glUseProgram(program);
        for(int block=0;block<GL20.glGetProgrami(program,GL31.GL_ACTIVE_UNIFORM_BLOCKS);block++) {
            String name=GL31.glGetActiveUniformBlockName(program,block);
            GL31.glUniformBlockBinding(program,block,block);GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,block,uniformNames.get(name));
        }
        int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
        ByteBuffer mesh=MemoryUtil.memAlloc(11*2*6*56);
        for(int shell=0;shell<2;shell++)for(int x=0;x<32;x+=3)for(int corner:new int[]{0,1,2,0,2,3}) {
            boolean right=corner==1||corner==2,top=corner>=2;
            float px=x-15.5F+(right?2.25F:-2.25F),z=2.6F+shell*3;
            mesh.putFloat(px).putFloat(top?8-shell*2:0).putFloat(z);
            mesh.putFloat(px).putFloat(1658+z);
            mesh.putFloat(top?1:0).putFloat(.5F).putFloat(shell==0?1:.75F).putFloat(1);
            mesh.putFloat(0).putFloat(0).putFloat(1);
            mesh.putInt(right?255:0).putInt(0);
        }
        mesh.flip();int buffer=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,mesh,GL15.GL_STATIC_DRAW);
        String[] attrs={"Position","UV0","Color","Normal"};int[] sizes={3,2,4,3},offsets={0,12,20,36};
        for(int i=0;i<4;i++) {
            int index=GL20.glGetAttribLocation(program,attrs[i]);GL20.glEnableVertexAttribArray(index);GL20.glVertexAttribPointer(index,sizes[i],GL11.GL_FLOAT,false,56,offsets[i]);
        }
        int uv1=GL20.glGetAttribLocation(program,"UV1");GL20.glEnableVertexAttribArray(uv1);GL30.glVertexAttribIPointer(uv1,2,GL11.GL_INT,56,48);
        int clock=GL20.glGetAttribLocation(program,"UV2");GL30.glVertexAttribI2i(clock,1234,0);
        GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glDepthMask(false);
        ByteBuffer before=MemoryUtil.memAlloc(640*416*4),after=MemoryUtil.memAlloc(640*416*4);
        GL11.glReadPixels(0,0,640,416,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,before);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,mesh.remaining()/56);
        GL11.glReadPixels(0,0,640,416,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,after);
        int changed=0;for(int i=0;i<640*416;i++)if(Math.abs((before.get(i*4)&255)-(after.get(i*4)&255))>3)changed++;
        if(changed<500)throw new AssertionError("Missing visible/depth-tested waterfall mist: "+changed);
        System.out.println("PASS actual local mist blend: "+changed+" affected pixels");
        MemoryUtil.memFree(before);MemoryUtil.memFree(after);MemoryUtil.memFree(mesh);
        GL11.glDepthMask(true);GL11.glDisable(GL11.GL_BLEND);GL15.glDeleteBuffers(buffer);GL30.glDeleteVertexArrays(vao);
        GL20.glDeleteProgram(program);GL20.glDeleteShader(vs);GL20.glDeleteShader(fs);
    }
    private static int compile(String material,String extension,int type) throws Exception {
        int shader=GL20.glCreateShader(type);
        GL20.glShaderSource(shader,ShaderIncludes.resolve(Files.readString(Path.of("src/main/resources/assets/asterion/shaders/core/"+material+"."+extension))));
        GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(GL20.glGetShaderInfoLog(shader));
        return shader;
    }
}
