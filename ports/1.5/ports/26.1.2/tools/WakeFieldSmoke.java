import net.krodark.asterion.update.underworld.world.FerryWakeField;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

public final class WakeFieldSmoke {
    public static void check() {
        var repeated=new FerryWakeField();
        for(int tick=0;tick<100;tick++)for(int frame=0;frame<8;frame++)repeated.record(0,tick*.072,tick,1);
        if(repeated.size()<5)throw new AssertionError("Repeated render frames erase wake anchors");
        var presence=new FerryWakeField();presence.rasterize(0,0,20);
        presence.addPresence(0,0,.3,.9,0,20,1);
        double contact=0;for(int z=0;z<128;z++)for(int x=0;x<128;x++)contact+=presence.foamAt(x,z);
        if(contact<=0)throw new AssertionError("Player water contact missing");
        presence.rasterize(0,0,21);
        for(int z=0;z<128;z++)for(int x=0;x<128;x++)if(presence.foamAt(x,z)!=0)throw new AssertionError("Player contact persists after exit");
        System.out.println("PASS repeat-frame wake stability and pose-sized player contact cleanup");
        var field=new FerryWakeField();
        for(int t=0;t<260;t++)field.record(Math.sin(t*.012)*3,170+t*.072,t,1);
        field.rasterize(0,185,260);
        float[] before=new float[128*128];
        int ox=field.originX,oz=field.originZ;double sum=0;
        for(int z=0;z<128;z++)for(int x=0;x<128;x++){before[z*128+x]=field.foamAt(x,z);sum+=before[z*128+x];}
        if(sum<1 || field.size()>64)throw new AssertionError("Wake history missing or unbounded");
        field.rasterize(8,193,260);
        int shiftX=(field.originX-ox)*2,shiftZ=(field.originZ-oz)*2;
        for(int z=0;z<100;z++)for(int x=0;x<100;x++) {
            int bx=x+shiftX,bz=z+shiftZ;
            if(bx>=0&&bx<128&&bz>=0&&bz<128&&Math.abs(field.foamAt(x,z)-before[bz*128+bx])>1e-6)
                throw new AssertionError("Wake moved with the sampling window");
        }
        field.rasterize(8,193,700);
        if(field.size()!=0)throw new AssertionError("Stopped wake never expires");
        System.out.println("PASS wake stays in world coordinates through window movement, has a bounded history, and expires.");
    }
    public static int upload(int program,boolean active) {
        var field=new FerryWakeField();
        if(active)for(int t=950;t<=1234;t++)field.record(Math.sin((t-1234)*.012)*3,186.8+(t-1234)*.072,t,1);
        field.rasterize(0,190,1234);
        var pixels=MemoryUtil.memAlloc(128*129*4);
        for(int z=0;z<129;z++)for(int x=0;x<128;x++) {
            int p=z<128?field.pixel(x,z):0;
            if(z==128&&x==0){int a=(field.originX+2048)*16,b=(field.originZ+2048)*16;
                p=(b&255)<<24|(a>>8&255)<<16|(a&255)<<8|(b>>8&255);}
            pixels.put((byte)(p>>16)).put((byte)(p>>8)).put((byte)p).put((byte)(p>>24));
        }
        pixels.flip();int texture=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,128,129,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        MemoryUtil.memFree(pixels);GL20.glUniform1i(GL20.glGetUniformLocation(program,"Sampler1"),1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);return texture;
    }
}
