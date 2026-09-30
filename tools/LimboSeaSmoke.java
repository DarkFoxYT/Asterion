import net.krodark.asterion.update.underworld.world.LimboSeaRegions;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import java.nio.file.*;
import com.google.gson.JsonParser;

/** Checks region scale, continuous palettes, resource wiring and actual driver output. */
public final class LimboSeaSmoke {
    public static void main(String[] args) throws Exception {
        check(LimboSeaRegions.sea(0,58)==LimboSeaRegions.Sea.STYX,"docks must be Styx");
        for (int i=0;i<5;i++) {
            int occupied=0;
            for(int z=58;z<16058;z++) {
                if(LimboSeaRegions.sea(0,z).ordinal()==i) occupied++;
                check(LimboSeaRegions.water(0,z).distanceTo(LimboSeaRegions.water(0,z+.01))<.00002,"water discontinuity");
                check(LimboSeaRegions.fog(0,z).distanceTo(LimboSeaRegions.fog(0,z+.01))<.00002,"fog discontinuity");
            }
            check(occupied>=2000,"sea shorter than 2000: "+i);
        }
        // Both sides of the sea share the same five connected regions.
        for(int x:new int[]{-16000,-8000,0,8000,16000})
            for(int z=200;z<18000;z+=137)
                check(LimboSeaRegions.sea(x,z).ordinal()==Math.min(4,(int)(LimboSeaRegions.distance(x,z)/3200)),"layout disagreement");
        var dimension=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/asterion/dimension/limbo.json"))).getAsJsonObject();
        var source=dimension.getAsJsonObject("generator").getAsJsonObject("biome_source");
        check(source.get("type").getAsString().equals("asterion:limbo_seas"),"wrong biome source");
        var biomes=source.getAsJsonArray("biomes");
        String[] names={"limbo","phlegethon","lethe","acheron","cocytus"};
        check(biomes.size()==5,"must have exactly five biomes");
        for(int i=0;i<5;i++) {
            check(biomes.get(i).getAsString().equals("asterion:"+names[i]),"wrong sea order");
            var biome=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/asterion/worldgen/biome/"+names[i]+".json"))).getAsJsonObject();
            check(biome.has("attributes")&&biome.getAsJsonObject("effects").has("water_color"),"missing biome visuals");
        }
        checkGpu();
        System.out.println("PASS five massive seas, Styx first, continuous gradients, resources and CPU/GPU palette agreement");
    }
    private static void checkGpu() throws Exception {
        check(GLFW.glfwInit(),"GLFW init");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(32,32,"Sea palette validation",0,0);
        check(window!=0,"OpenGL context");
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            int vs=compile(GL20.GL_VERTEX_SHADER,"#version 330\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.-1.,0,1);}");
            int fs=compile(GL20.GL_FRAGMENT_SHADER,ShaderIncludes.resolve("""
                    #version 330
                    #moj_import <asterion:limbo_depths.glsl>
                    uniform vec2 Position; uniform int Fog;
                    uniform float Height, Travel, Clock; uniform int Count;
                    out vec4 color;
                    void main(){
                        if(Fog==3){
                            vec2 uv=gl_FragCoord.xy/vec2(640,360);
                            vec3 camera=vec3(Position.x,3.5,Position.y);
                            vec3 ray=normalize(vec3((uv.x-.5)*1.777,uv.y-.67,1));
                            float hit=ray.y<-.0001?-camera.y/ray.y:512;
                            if(ray.y<-.0001)for(int i=0;i<4;i++)hit=max(0.0,(sampleWave((camera+ray*hit).xz,Clock).x-camera.y)/ray.y);
                            vec2 hitPosition=(camera+ray*hit).xz;
                            vec4 swell=sampleWave(hitPosition,Clock);
                            vec3 surface=ray.y<-.0001?limboFireSurfaceLit(hitPosition,Clock,swell.x,vec3(-swell.y,1,-swell.z),-ray):vec3(.014,.006,.003);
                            vec4 fire=limboFireVolume(camera,ray,min(hit,512),0,Clock,Count);
                            color=vec4(surface*fire.a+fire.rgb,1);return;
                        }
                        if(Fog==7){
                            vec2 uv=gl_FragCoord.xy/vec2(640,360);
                            vec3 camera=vec3(Position.x,3.5,Position.y);
                            vec3 ray=normalize(vec3((uv.x-.5)*1.777,uv.y-.67,1));
                            float hit=ray.y<-.0001?-camera.y/ray.y:512;
                            if(ray.y<-.0001)for(int i=0;i<5;i++)hit=max(0.0,(sampleWave((camera+ray*hit).xz,Clock).x-camera.y)/ray.y);
                            vec2 p=(camera+ray*hit).xz;
                            vec4 swell=sampleWave(p,Clock);
                            LimboSeaStyle s=limboSeaStyle(p);
                            vec3 n=normalize(vec3(-swell.y,1,-swell.z));
                            vec3 surface=mix(limboAcheronSurface(p,Clock,swell.x,n,-ray,0),
                                limboCocytusSurface(p,Clock,swell.x,n,-ray,0),s.tears);
                            surface=mix(surface,limboLetheSurface(p,Clock,n,-ray,0),s.oblivion);
                            if(ray.y>=-.0001)surface=limboSeaHorizon(camera,ray)*.6;
                            vec4 volume=limboSeaVolume(camera,ray,min(hit,512),0,Clock,Count);
                            color=vec4(surface*volume.a+volume.rgb,1);return;
                        }
                        if(Fog==10){color=limboSeaVolume(vec3(Position.x,Height,Position.y),vec3(0,0,1),Travel,0,Clock,Count);return;}
                        if(Fog==8){color=limboDepthMist(vec3(Position.x,Height,Position.y),vec3(0,0,1),Travel,0,Clock,Count);return;}
                        if(Fog==9){color=vec4(limboAcheronSurface(Position,Clock,Height,vec3(.4,1,.2),normalize(vec3(.3,.3,-1)),0),1);return;}
                        if(Fog==6){color=limboLetheVolume(vec3(Position.x,Height,Position.y),vec3(0,0,1),Travel,0,Clock,Count);return;}
                        if(Fog==5){color=limboFireField(vec3(Position.x,Height,Position.y),0,Clock);return;}
                        if(Fog==2){color=limboFireVolume(vec3(Position.x,Height,Position.y),vec3(0,0,1),Travel,0,Clock,Count);return;}
                        if(Fog==4){color=vec4(limboFireSurfaceLit(Position,Clock,Height,vec3(.4,1,.2),normalize(vec3(.3,.3,-1))),1);return;}
                        LimboSeaStyle s=limboSeaStyle(Position);color=vec4(Fog==1?s.fog:s.water,1);
                    }
                    """));
            int program=GL20.glCreateProgram();GL20.glAttachShader(program,vs);GL20.glAttachShader(program,fs);GL20.glLinkProgram(program);
            check(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(program));
            int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);GL20.glUseProgram(program);GL11.glViewport(0,0,32,32);
            for(int z=58;z<16058;z+=113) for(int fog=0;fog<2;fog++) {
                GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),0,z);
                GL20.glUniform1i(GL20.glGetUniformLocation(program,"Fog"),fog);
                GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
                float[] pixel=new float[4];GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,pixel);
                Vec3 expected=fog==1?LimboSeaRegions.fog(0,z):LimboSeaRegions.water(0,z);
                check(new Vec3(pixel[0],pixel[1],pixel[2]).distanceTo(expected)<.006,"GPU palette mismatch at "+z);
            }
            checkFire(program);
            checkLethe(program);
            checkDepths(program);
            GL30.glDeleteVertexArrays(vao);GL20.glDeleteProgram(program);GL20.glDeleteShader(vs);GL20.glDeleteShader(fs);
        } finally {GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static int compile(int type,String text) {
        int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,text);GL20.glCompileShader(shader);
        check(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(shader));return shader;
    }
    private static void checkFire(int program) {
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"Fog"),4);
        GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),1.5F,4858);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Height"),-2);
        float[] trough=read();
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Height"),2);
        float[] crest=read();
        check(crest[0]+crest[1]+crest[2]>2*(trough[0]+trough[1]+trough[2]),"surface crests and troughs must have depth contrast");
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"Fog"),2);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Height"),1.5F);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Travel"),16);
        for(int count:new int[]{8,12,15,20,28}) {
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"Count"),count);
            GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),1.5F,4858);
            float minimum=1,maximum=0,warm=0;
            for(int time=0;time<=120;time+=5) {
                GL20.glUniform1f(GL20.glGetUniformLocation(program,"Clock"),time);
                float green=0;
                for(int offset=0;offset<5;offset++) {
                    GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),1.5F+offset*.35F,4858+offset*.5F);
                    float[] pixel=read();
                    warm=Math.max(warm,pixel[0]-pixel[2]);green+=pixel[1];
                    check(Float.isFinite(pixel[0])&&pixel[3]>=0&&pixel[3]<=1,"invalid flame output");
                }
                minimum=Math.min(minimum,green);maximum=Math.max(maximum,green);
            }
            check(maximum-minimum>.015,"flames must animate");
            check(warm>.1,"fire should glow warm at every quality");
        }
        GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),0,58);
        checkClear(read(),"fire leaked into Styx");
        GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),1.5F,4858);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Height"),20);
        checkClear(read(),"fire outside flame layer");
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Height"),1.5F);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Travel"),0);
        checkClear(read(),"fire behind scene depth");
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Travel"),16);
        // Time the isolated actual flame shader at the medium-resolution pass size.
        int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA16F,640,360,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
        int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
        check(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"fire timing target");
        GL11.glViewport(0,0,640,360);
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"Fog"),3);
        int query=GL15.glGenQueries();
        for(int count:new int[]{12,20,28}) {
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"Count"),count);
            for(int i=0;i<4;i++)GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,query);
            for(int i=0;i<16;i++)GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            long nanos=GL33.glGetQueryObjectui64(query,GL15.GL_QUERY_RESULT);
            System.out.printf("Fire preview GPU synthetic (includes surface/intersection): %d samples, 640x360, %.3f ms on %s%n",count,nanos/16000000.0,GL11.glGetString(GL11.GL_RENDERER));
        }
        float[] pixels=new float[640*360*4];
        GL11.glReadPixels(0,0,640,360,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
        var preview=new java.awt.image.BufferedImage(640,360,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<360;y++)for(int x=0;x<640;x++) {
            int rgb=0;
            for(int channel=0;channel<3;channel++) {
                float linear=pixels[(y*640+x)*4+channel];
                int value=(int)Math.round(Math.pow(1-Math.exp(-Math.max(0,linear)),1/2.2)*255);
                rgb=(rgb<<8)|value;
            }
            preview.setRGB(x,359-y,rgb);
        }
        try {
            var output=Path.of("build/reports/phlegethon-fire.png");Files.createDirectories(output.getParent());
            javax.imageio.ImageIO.write(preview,"png",output.toFile());
        } catch(java.io.IOException e) {throw new AssertionError("fire preview",e);}
        GL15.glDeleteQueries(query);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);
        GL30.glDeleteFramebuffers(fbo);GL11.glDeleteTextures(texture);
        System.out.println("PASS volumetric flame colour, animation, quality budgets, sea/slab/depth rejection");
    }
    private static void checkLethe(int program) {
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"Fog"),6);
        GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),0,8058);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Height"),2);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Travel"),64);
        for(int count:new int[]{8,15,28}) {
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"Count"),count);
            GL20.glUniform1f(GL20.glGetUniformLocation(program,"Clock"),0);
            float[] first=read();
            check(first[3]<.95&&first[0]>first[2],"Lethe needs warm opaque mist at every quality");
            GL20.glUniform1f(GL20.glGetUniformLocation(program,"Clock"),1600);
            float[] later=read();
            check(Math.abs(first[0]-later[0])+Math.abs(first[3]-later[3])>.0001,"Lethe mist must drift");
        }
        for(int z:new int[]{58,4858,11258,14558}) {
            GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),0,z);
            checkClear(read(),"Lethe mist leaked into another sea");
        }
        GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),0,8058);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Height"),20);
        checkClear(read(),"Lethe mist above layer");
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Height"),2);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Travel"),0);
        checkClear(read(),"Lethe mist behind scene depth");
        System.out.println("PASS Lethe mist animation, quality modes, region/slab/depth rejection");
    }
    private static void checkDepths(int program) throws Exception {
        GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),1.5F,4858);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Height"),1.5F);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Travel"),16);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Clock"),1200);
        for(int count:new int[]{8,15,28}) {
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"Count"),count);
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"Fog"),2);
            float[] original=read();
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"Fog"),10);
            float[] combined=read();
            for(int channel=0;channel<4;channel++)
                check(Math.abs(original[channel]-combined[channel])<.00001,"Fire sea changed at quality "+count);
        }
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"Fog"),9);
        GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),0,11258);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Height"),-2);
        float[] trough=read();
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Height"),2);
        float[] crest=read();
        check(crest[1]>crest[0]*1.5&&crest[1]>crest[2]*1.5,"Acheron must read as green");
        check(crest[1]>trough[1]*1.5,"Acheron needs pronounced crest contrast");
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"Fog"),8);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Height"),2);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Travel"),64);
        for(int z:new int[]{11258,14558})for(int count:new int[]{8,15,28}) {
            GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),0,z);
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"Count"),count);
            GL20.glUniform1f(GL20.glGetUniformLocation(program,"Clock"),0);
            float[] first=read();
            check(first[3]<.98&&first[3]>0,"Regional mist missing at quality "+count);
            if(z==11258)check(first[1]>first[0]&&first[1]>first[2],"Acheron fog must be green");
            GL20.glUniform1f(GL20.glGetUniformLocation(program,"Clock"),1600);
            float[] later=read();
            check(Math.abs(first[1]-later[1])+Math.abs(first[3]-later[3])>.0001,"Regional mist must animate");
        }
        for(int z:new int[]{58,4858,8058}) {
            GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),0,z);
            checkClear(read(),"Deep-sea mist leaked into Styx, fire or Lethe");
        }
        GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),0,11258);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Travel"),0);
        checkClear(read(),"Regional mist behind geometry");
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Travel"),64);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Height"),24);
        checkClear(read(),"Regional mist above layer");
        int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA16F,640,360,0,GL11.GL_RGBA,GL11.GL_FLOAT,0L);
        int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
        check(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"sea preview target");
        GL11.glViewport(0,0,640,360);
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"Fog"),7);
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"Count"),15);
        GL20.glUniform1f(GL20.glGetUniformLocation(program,"Clock"),1800);
        String[] names={"lethe","acheron","cocytus"};
        int[] locations={8058,11258,14558};
        for(int sea=0;sea<3;sea++) {
            GL20.glUniform2f(GL20.glGetUniformLocation(program,"Position"),0,locations[sea]);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            float[] pixels=new float[640*360*4];
            GL11.glReadPixels(0,0,640,360,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
            var preview=new java.awt.image.BufferedImage(640,360,java.awt.image.BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<360;y++)for(int x=0;x<640;x++) {
                int rgb=0;
                for(int channel=0;channel<3;channel++) {
                    float linear=pixels[(y*640+x)*4+channel];
                    check(Float.isFinite(linear),"Invalid sea preview pixel");
                    int value=(int)Math.round(Math.pow(1-Math.exp(-Math.max(0,linear)),1/2.2)*255);
                    rgb=(rgb<<8)|value;
                }
                preview.setRGB(x,359-y,rgb);
            }
            var output=Path.of("build/reports/"+names[sea]+"-sea.png");
            Files.createDirectories(output.getParent());
            javax.imageio.ImageIO.write(preview,"png",output.toFile());
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);
        GL30.glDeleteFramebuffers(fbo);GL11.glDeleteTextures(texture);
        System.out.println("PASS green Acheron crests, regional mist, quality/depth/region checks and rendered sea previews");
    }
    private static float[] read() {
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
        float[] pixel=new float[4];GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,pixel);return pixel;
    }
    private static void checkClear(float[] pixel,String message) {
        check(pixel[0]+pixel[1]+pixel[2]<.005&&pixel[3]>.995,message);
    }
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
}
