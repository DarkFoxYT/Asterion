import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import org.joml.Matrix4f;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

/** Controlled preview of the actual water shaders, mesh, and vanilla water texture. */
public final class WaterVisualSmoke {
    public static void render(int program) throws Exception {
        render(program, false);
        render(program, true);
    }
    private static void render(int program, boolean boat) throws Exception {
        int width=960,height=540;
        GL20.glUseProgram(program);
        int wakeTexture=WakeFieldSmoke.upload(program,boat);
        int vao=GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao);
        int output=GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D,output);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,width,height,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
        int fbo=GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,output,0);
        int depth=GL30.glGenRenderbuffers(); GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL14.GL_DEPTH_COMPONENT24,width,height);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Preview framebuffer incomplete");
        List<Integer> buffers=new ArrayList<>();
        for(int i=0;i<GL20.glGetProgrami(program,GL31.GL_ACTIVE_UNIFORM_BLOCKS);i++) {
            String name=GL31.glGetActiveUniformBlockName(program,i);
            float[] data=new float[GL31.glGetActiveUniformBlocki(program,i,GL31.GL_UNIFORM_BLOCK_DATA_SIZE)/4];
            if(name.equals("Projection")) new Matrix4f().perspective((float)Math.toRadians(65),width/(float)height,.05F,512).get(data);
            else if(name.equals("DynamicTransforms")) {
                new Matrix4f().lookAt(0,0,0,12,-4.1F,46,0,1,0).get(data);
                Arrays.fill(data,16,20,1);
            } else if(name.equals("Fog")) {
                data[0]=.075F;data[1]=.095F;data[2]=.12F;data[3]=1;
                data[4]=100;data[5]=240;data[6]=240;data[7]=300;data[8]=300;data[9]=300;
            }
            int b=GL15.glGenBuffers();buffers.add(b);GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,b);
            GL15.glBufferData(GL31.GL_UNIFORM_BUFFER,data,GL15.GL_STATIC_DRAW);
            GL31.glUniformBlockBinding(program,i,i);GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,i,b);
        }
        float[] vertices=new float[192*240*6*5];int at=0;
        short[] hull = new short[192*240*6*2]; int hullAt=0;
        int[] corners={0,1,2,0,2,3};
        for(int z=120;z<360;z++) for(int x=-96;x<96;x++) for(int c:corners) {
            int vx=x+(c>=2?1:0),vz=z+(c==1||c==2?1:0);
            vertices[at++]=vx+8;vertices[at++]=47+8F/9F-52;vertices[at++]=vz-174;
            vertices[at++]=vx;vertices[at++]=vz;
            hull[hullAt++]=(short)(vx*128); hull[hullAt++]=(short)((vz-190)*128);
        }
        int mesh=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,mesh);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STATIC_DRAW);
        int position=GL20.glGetAttribLocation(program,"Position"),uv=GL20.glGetAttribLocation(program,"UV0");
        GL20.glEnableVertexAttribArray(position);GL20.glVertexAttribPointer(position,3,GL11.GL_FLOAT,false,20,0L);
        GL20.glEnableVertexAttribArray(uv);GL20.glVertexAttribPointer(uv,2,GL11.GL_FLOAT,false,20,12L);
        int hullBuffer=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,hullBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER,hull,GL15.GL_STATIC_DRAW);
        int overlay=GL20.glGetAttribLocation(program,"UV1");GL20.glEnableVertexAttribArray(overlay);
        GL30.glVertexAttribIPointer(overlay,2,GL11.GL_SHORT,4,0L);
        GL20.glVertexAttrib4f(GL20.glGetAttribLocation(program,"Color"),1,.5F,224F/255,boat?224F/255:0);
        float boatHeight=(float)(net.krodark.asterion.update.underworld.world.UnderworldWaves.height(0,190,1234.5)-.239)/8;
        GL20.glVertexAttrib3f(GL20.glGetAttribLocation(program,"Normal"),1,boatHeight,0);
        GL30.glVertexAttribI2i(GL20.glGetAttribLocation(program,"UV2"),1234,0);
        BufferedImage texture;
        try(var stream=WaterVisualSmoke.class.getClassLoader().getResourceAsStream("assets/minecraft/textures/block/water_still.png")){texture=ImageIO.read(stream);}
        ByteBuffer pixels=MemoryUtil.memAlloc(texture.getWidth()*texture.getHeight()*4);
        for(int y=0;y<texture.getHeight();y++)for(int x=0;x<texture.getWidth();x++){
            int rgb=texture.getRGB(x,y);pixels.put((byte)(rgb>>16)).put((byte)(rgb>>8)).put((byte)rgb).put((byte)(rgb>>24));
        }
        pixels.flip();int tex=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,texture.getWidth(),texture.getHeight(),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        MemoryUtil.memFree(pixels);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glViewport(0,0,width,height);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_BLEND);
        GL11.glClearColor(.075F,.095F,.12F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,vertices.length/5);
        pixels=MemoryUtil.memAlloc(width*height*4);GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);int white=0;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int i=(y*width+x)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;
            // Muted gray-green froth must still separate visibly from the dark body.
            image.setRGB(x,height-1-y,(r<<16)|(g<<8)|b);if(r>85&&g>100&&b>80 && g>=b)white++;
        }
        MemoryUtil.memFree(pixels);Files.createDirectories(Path.of("build/reports"));
        ImageIO.write(image,"png",Path.of(boat ? "build/reports/limbo-water-hull-preview.png" : "build/reports/limbo-water-preview.png").toFile());
        if(white<100)throw new AssertionError("Muted foam is not visibly distinct: "+white);
        if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Preview GL error");
        System.out.println("PASS rendered wave preview: "+white+" bright foam pixels.");
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);GL20.glUseProgram(0);
        for(int b:buffers)GL15.glDeleteBuffers(b);GL15.glDeleteBuffers(mesh);GL15.glDeleteBuffers(hullBuffer);GL30.glDeleteVertexArrays(vao);
        GL30.glDeleteRenderbuffers(depth);GL30.glDeleteFramebuffers(fbo);GL11.glDeleteTextures(output);GL11.glDeleteTextures(tex);GL11.glDeleteTextures(wakeTexture);
    }
}
