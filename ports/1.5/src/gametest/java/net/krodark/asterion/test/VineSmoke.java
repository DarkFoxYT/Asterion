package net.krodark.asterion.test;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.LabyrinthVineBlock;
import net.krodark.asterion.port.client.LabyrinthVinePortRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Same-client ABBA comparison: identical geometry, lighting and camera, culling only. */
final class VineSmoke {
    private static final java.util.List<Double> baseline = new java.util.ArrayList<>(), culled = new java.util.ArrayList<>();
    private static long previous;
    private static java.lang.reflect.Field frustum;
    static void register(java.util.function.IntSupplier ticks) {
        if (!Boolean.getBoolean("asterion.vineSmoke")) return;
        try { frustum = LabyrinthVinePortRenderer.class.getDeclaredField("frustum"); frustum.setAccessible(true); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            int t=ticks.getAsInt();
            try { if (t>=240 && t<320 || t>=480 && t<560) frustum.set(null,null); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(context -> {
            int t=ticks.getAsInt();long now=System.nanoTime();
            if(previous!=0 && (t>=260 && t<320 || t>=500 && t<560)) baseline.add((now-previous)/1e6);
            if(previous!=0 && (t>=340 && t<400 || t>=420 && t<480)) culled.add((now-previous)/1e6);
            previous=now;
        });
    }
    static void tick(Minecraft client,int tick) {
        client.player.setPos(.5,178,-4.5);client.player.setYRot(0);client.player.yRotO=0;
        client.player.setXRot(-12);client.player.xRotO=-12;
        client.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if(tick==160) {
            client.options.enableVsync().set(false);client.options.framerateLimit().set(260);
            GameplaySmoke.server(client,p -> {
                var level=p.serverLevel();
                for(int x=-24;x<=24;x+=2) for(int z=-24;z<=24;z+=2) {
                    level.setBlock(new BlockPos(x,185,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
                    for(int y=182;y<185;y++) level.setBlock(new BlockPos(x,y,z),Asterion.LABYRINTH_VINE.defaultBlockState().setValue(LabyrinthVineBlock.END,y==182),2);
                }
            });
        }
        if(tick==570) {
            if(baseline.size()<30 || culled.size()<30) throw new AssertionError("Insufficient render samples");
            baseline.sort(Double::compare);culled.sort(Double::compare);
            Asterion.LOGGER.info("ASTERION_VINES ABBA baseline median={} p95={} frames={}; culled median={} p95={} frames={}",
                    baseline.get(baseline.size()/2),baseline.get((int)(baseline.size()*.95)),baseline.size(),
                    culled.get(culled.size()/2),culled.get((int)(culled.size()*.95)),culled.size());
            ClientSmokeTest.verifyGraphicsAndTaa();
            Asterion.LOGGER.info("ASTERION_VINES PASSED: 1875 identical Geo vines, terminal bloom, unchanged quality, no graphics errors");
            client.stop();
        }
    }
}
