package net.krodark.asterion.test;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
final class EmissionCullingRegression {
    static void run(Minecraft client) {
        try {
            var cls=Class.forName("net.krodark.asterion.port.client.PortEmissiveQueue");
            var visible=cls.getDeclaredMethod("visible",Matrix4f.class,AABB.class);visible.setAccessible(true);
            var bounds=new AABB(-.5,-.5,-.5,.5,.5,.5);
            if(!(boolean)visible.invoke(null,new Matrix4f(),bounds)
                    || (boolean)visible.invoke(null,new Matrix4f().translation(10,0,0),bounds)
                    || !(boolean)visible.invoke(null,new Matrix4f().translation(1.25F,0,0),bounds))
                throw new AssertionError("Emission frustum rejected visible/edge geometry or accepted off-camera geometry");
            var poolField=cls.getDeclaredField("POOL");poolField.setAccessible(true);
            var pool=(java.util.List<?>)poolField.get(null);
            if(pool.isEmpty())throw new AssertionError("No real emission submissions to test");
            var reset=cls.getDeclaredMethod("resetFrame");reset.setAccessible(true);reset.invoke(null);
            for(Object draw:pool) for(String field:new String[]{"bone","renderer","texture"}) {
                var f=draw.getClass().getDeclaredField(field);f.setAccessible(true);
                if(f.get(draw)!=null)throw new AssertionError("Emission queue retained stale "+field);
            }
            var world=cls.getDeclaredField("world");world.setAccessible(true);world.set(null,null);
            var tick=cls.getDeclaredMethod("tick",Minecraft.class);tick.setAccessible(true);tick.invoke(null,client);
            if(!pool.isEmpty())throw new AssertionError("World change retained emission pool");
            var key=new net.minecraft.core.BlockPos(300000,180,300000);
            if(client.level.getChunkSource().hasChunk(key.getX() >> 4,key.getZ() >> 4))throw new AssertionError("Unload fixture unexpectedly loaded");
            net.krodark.asterion.port.client.PortLight.updateItemGlowLight(key,net.minecraft.world.phys.Vec3.atCenterOf(key),1,1,1,1,4,false);
            net.krodark.asterion.port.client.PortLight.tickCleanup(client);
            var updated=net.krodark.asterion.port.client.PortLight.class.getDeclaredField("UPDATED");updated.setAccessible(true);
            if(((java.util.Map<?,?>)updated.get(null)).containsKey(key))throw new AssertionError("Unloaded chunk retained light");
            Asterion.LOGGER.info("ASTERION_EMISSION_CULLING PASSED: visible/edge bounds kept, off-camera bounds rejected, frame references released, world pool cleared, unloaded light removed");
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
}
