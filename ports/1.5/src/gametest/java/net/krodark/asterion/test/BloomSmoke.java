package net.krodark.asterion.test;

import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/** Reads the actual pre-blur emission target after a camera turn and full occlusion. */
final class BloomSmoke {
    private static double centerX, rotatedCenterX;
    private static int lastCapture=-1;
    static void register(java.util.function.IntSupplier ticks) {
        if(!Boolean.getBoolean("asterion.bloomSmoke")) return;
        Pipeline.add(RenderStage.POST, 11, "Asterion bloom regression", context -> {
            int tick=ticks.getAsInt();
            if(tick==lastCapture || tick!=240 && tick!=280 && tick!=310 && tick!=317 && tick!=360 && tick!=420 && tick!=455) return;
            lastCapture=tick;
            inspect(tick);
        });
    }
    static void tick(Minecraft client,int tick) {
        net.krodark.asterion.AsterionConfig.INSTANCE.deadSunEnabled=false;
        net.krodark.asterion.AsterionConfig.INSTANCE.dustyAirEnabled=false;
        Bloom.settings().threshold(100F).occlude(true).enabled(true);
        client.player.setPos(tick<290?.5:2.5,178,-6.5);
        client.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        float yaw=tick>=315 && tick<320?180:tick<260?0:12;
        client.player.setYRot(yaw);client.player.yRotO=yaw;
        client.player.setXRot(tick>=370?-8:-25);client.player.xRotO=client.player.getXRot();
        if(tick>=370) {client.player.setYRot(0);client.player.yRotO=0;}
        if(tick==320) GameplaySmoke.server(client,p -> p.serverLevel().getServer().getCommands().performPrefixedCommand(p.serverLevel().getServer().createCommandSourceStack(),"execute in asterion:labyrinth run fill -25 160 -1 25 215 -1 minecraft:stone"));
        if(tick==370) GameplaySmoke.server(client,p -> {
            p.serverLevel().getServer().getCommands().performPrefixedCommand(p.serverLevel().getServer().createCommandSourceStack(),"execute in asterion:labyrinth run fill -25 160 -1 25 215 -1 minecraft:air");
            for(var boss:p.serverLevel().getEntitiesOfClass(net.krodark.asterion.entity.MinotaurEntity.class,p.getBoundingBox().inflate(100))) {
                boss.setNoGravity(true);boss.moveTo(.5,178,70,180,0);
                boss.yBodyRot=boss.yHeadRot=180;
            }
        });
        if(tick==425) GameplaySmoke.server(client,p -> {
            for(var boss:p.serverLevel().getEntitiesOfClass(net.krodark.asterion.entity.MinotaurEntity.class,p.getBoundingBox().inflate(100))) {
                boss.setYRot(0);boss.yBodyRot=boss.yHeadRot=0;
            }
        });
        if(tick>=425) for(var entity:client.level.entitiesForRendering())
            if(entity instanceof net.krodark.asterion.entity.MinotaurEntity boss) {
                boss.setYRot(0);boss.yRotO=0;boss.yBodyRot=boss.yBodyRotO=boss.yHeadRot=boss.yHeadRotO=0;
            }
        if(tick==470) {
            if(lastCapture!=455) throw new AssertionError("Distant bloom checks never rendered");
            Asterion.LOGGER.info("ASTERION_BLOOM PASSED: bright eyes, camera transforms, wall occlusion, distant model self-occlusion, TAA off");
            EmissionCullingRegression.run(client);
            ClientSmokeTest.verifyGraphicsAndTaa();
            client.stop();
        }
    }
    private static void inspect(int tick) {
        try {
            var rendererField=Bloom.class.getDeclaredField("RENDERER");rendererField.setAccessible(true);
            Object renderer=rendererField.get(null);
            var bufferField=renderer.getClass().getDeclaredField("emissiveBuf");bufferField.setAccessible(true);
            Framebuffer buffer=(Framebuffer)bufferField.get(renderer);
            if(buffer==null) throw new AssertionError("No emissive target");
            int width=buffer.width(),height=buffer.height();
            var main=Minecraft.getInstance().getMainRenderTarget();
            if(width!=main.width || height!=main.height) throw new AssertionError("Emission depth must match the world resolution");
            var data=MemoryUtil.memAllocFloat(width*height*3);
            int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            double weight=0,x=0,y=0;int pixels=0;
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,buffer.colorTextureGlId(0));
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGB,GL11.GL_FLOAT,data);
                try(var mask=new com.mojang.blaze3d.platform.NativeImage(width,height,false)) {
                for(int i=0;i<width*height;i++) {
                    int red=Math.min(255,Math.round(data.get(i*3)*255));
                    int green=Math.min(255,Math.round(data.get(i*3+1)*255));
                    int blue=Math.min(255,Math.round(data.get(i*3+2)*255));
                    mask.setPixelRGBA(i%width,height-1-i/width,0xFF000000|blue<<16|green<<8|red);
                    double brightness=Math.max(data.get(i*3),Math.max(data.get(i*3+1),data.get(i*3+2)));
                    if(brightness>.01) {pixels++;weight+=brightness;x+=(i%width)*brightness;y+=(i/width)*brightness;}
                }
                var folder=Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
                java.nio.file.Files.createDirectories(folder);
                mask.writeToFile(folder.resolve("asterion-emission-mask-"+tick+".png"));
                }
            } finally {GL11.glBindTexture(GL11.GL_TEXTURE_2D,old);MemoryUtil.memFree(data);}
            Asterion.LOGGER.info("ASTERION_BLOOM tick={} pixels={} weight={} centroid=({}, {}) size={}x{}",tick,pixels,weight,x/Math.max(weight,.0001),y/Math.max(weight,.0001),width,height);
            if(tick==240) { if(pixels<2 || weight/pixels<.4)throw new AssertionError("Visible Minotaur eyes lost their full-bright texture");centerX=x/weight; }
            if(tick==280) { if(pixels<2 || centerX-x/weight < width*.04) throw new AssertionError("Bloom did not follow the world geometry when the camera turned"); rotatedCenterX=x/weight; }
            if(tick==310 && (pixels<2 || Math.abs(x/weight-rotatedCenterX)<width*.025)) throw new AssertionError("Bloom did not follow camera translation");
            if(tick==317 && pixels>0) throw new AssertionError("Off-camera geometry still contributes emission: "+pixels);
            if(tick==420 && pixels<1) throw new AssertionError("Distant forward-facing eyes lost their emission");
            if(tick==455 && pixels>0) throw new AssertionError("Distant emission leaked through the Minotaur's own head: "+pixels);
            if(tick==360 && pixels>0) throw new AssertionError("Emission leaked through a fully occluding stone wall: "+pixels+" pixels");
            var client=Minecraft.getInstance();
            net.minecraft.client.Screenshot.grab(client.gameDirectory,"asterion-bloom-regression-"+tick+".png",client.getMainRenderTarget(),message -> {});
        } catch(ReflectiveOperationException | java.io.IOException error) {throw new AssertionError(error);}
    }
}
