package net.krodark.asterion.test;

import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/** Compare the rendered sky and opaque surfaces with dust off/on at every quality. */
final class AtmosphereSmoke {
    private static int lastCapture=-1, completed;
    private static float[] baseline;
    private static boolean[] sky;
    static void register(java.util.function.IntSupplier ticks) {
        if(!Boolean.getBoolean("asterion.atmosphereSmoke")) return;
        Pipeline.add(RenderStage.OVERLAY,999,"Asterion atmosphere regression",context -> {
            int tick=ticks.getAsInt(), offset=(tick-160)%140;
            if(tick<160 || tick==lastCapture || offset!=60 && offset!=120) return;
            lastCapture=tick;
            inspect((tick-160)/140,offset==120);
        });
    }
    static void tick(Minecraft client,int tick) {
        AsterionConfig config=AsterionConfig.INSTANCE;
        config.deadSunEnabled=false;
        config.cinematicQuality=Math.min(2,(tick-160)/140);
        config.dustyAirEnabled=(tick-160)%140>=70;
        com.meekdev.amnetic.client.bloom.Bloom.settings().enabled(false);
        client.player.setPos(.5,178,-4.5);
        client.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        client.player.setYRot(0);client.player.yRotO=0;
        client.player.setXRot(-20);client.player.xRotO=-20;
        if(tick==180) GameplaySmoke.server(client,p -> p.serverLevel().getServer().getCommands().performPrefixedCommand(p.serverLevel().getServer().createCommandSourceStack(),"execute in asterion:asterion_dimension run fill -5 178 12 -1 190 12 minecraft:stone"));
        if(tick==575) {
            if(completed!=3) throw new AssertionError("Not all atmosphere qualities rendered");
            ClientSmokeTest.verifyGraphicsAndTaa();
            Asterion.LOGGER.info("ASTERION_ATMOSPHERE PASSED: dust covers sky and geometry at all 3 qualities, neutral sky, TAA off, no OpenGL errors");
            client.stop();
        }
    }
    private static void inspect(int quality,boolean enabled) {
        var client=Minecraft.getInstance();var target=client.getMainRenderTarget();
        int width=target.width,height=target.height,count=width*height;
        var rgb=MemoryUtil.memAllocFloat(count*3);var depth=MemoryUtil.memAllocFloat(count);
        int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,target.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGB,GL11.GL_FLOAT,rgb);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,target.getDepthTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
            if(!enabled) {
                baseline=new float[count*3];rgb.get(0,baseline);sky=new boolean[count];
                for(int i=0;i<count;i++) sky[i]=depth.get(i)>=.9999F;
            } else {
                if(baseline==null || baseline.length!=count*3) throw new AssertionError("Test window changed resolution");
                double skyDifference=0,wallDifference=0,red=0,green=0,blue=0,luma=0,luma2=0;int skyCount=0,wallCount=0;
                for(int y=40;y<height-40;y++) for(int x=40;x<width-40;x++) {
                    int i=y*width+x;boolean isSky=depth.get(i)>=.9999F;
                    if(isSky!=sky[i]) continue;
                    float r=rgb.get(i*3),g=rgb.get(i*3+1),b=rgb.get(i*3+2);
                    double difference=(Math.abs(r-baseline[i*3])+Math.abs(g-baseline[i*3+1])+Math.abs(b-baseline[i*3+2]))/3;
                    if(isSky) {
                        skyCount++;skyDifference+=difference;red+=r;green+=g;blue+=b;
                        double light=r*.2126+g*.7152+b*.0722;luma+=light;luma2+=light*light;
                    } else {wallCount++;wallDifference+=difference;}
                }
                double variance=luma2/Math.max(1,skyCount)-Math.pow(luma/Math.max(1,skyCount),2);
                Asterion.LOGGER.info("ASTERION_ATMOSPHERE quality={} skyPixels={} geometryPixels={} skyChange={} geometryChange={} skyRGB=({},{},{}) skyDeviation={}",quality,skyCount,wallCount,skyDifference/Math.max(1,skyCount),wallDifference/Math.max(1,wallCount),red/Math.max(1,skyCount),green/Math.max(1,skyCount),blue/Math.max(1,skyCount),Math.sqrt(Math.max(0,variance)));
                if(skyCount<1000 || wallCount<1000) throw new AssertionError("Fixture must contain both open sky and geometry");
                if(skyDifference/skyCount<.01 || wallDifference/wallCount<.003) throw new AssertionError("Dust missing over sky or walls at quality "+quality);
                if(blue/skyCount>red/skyCount+.03) throw new AssertionError("Sky developed a blue cast");
                if(variance<.00000025) throw new AssertionError("Dust banks disappeared into a flat sky");
                completed++;
            }
            try(var screenshot=new com.mojang.blaze3d.platform.NativeImage(width,height,false)) {
                for(int i=0;i<count;i++) {
                    int r=Math.min(255,Math.round(rgb.get(i*3)*255)),g=Math.min(255,Math.round(rgb.get(i*3+1)*255)),b=Math.min(255,Math.round(rgb.get(i*3+2)*255));
                    screenshot.setPixelRGBA(i%width,height-1-i/width,0xFF000000|b<<16|g<<8|r);
                }
                var folder=client.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
                screenshot.writeToFile(folder.resolve("asterion-atmosphere-"+quality+(enabled?"-on":"-off")+".png"));
            }
        } catch(java.io.IOException error) {throw new AssertionError(error);}
        finally {GL11.glBindTexture(GL11.GL_TEXTURE_2D,old);MemoryUtil.memFree(rgb);MemoryUtil.memFree(depth);}
    }
}
