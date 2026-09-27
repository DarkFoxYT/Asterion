package net.krodark.asterion.test;
import net.krodark.asterion.port.client.ragdoll.DismembermentEngine;
import net.minecraft.world.phys.Vec3;
/** Reflection keeps test inspection outside NeoForge's production Java module/package. */
public final class PhysicsSmokeAssertions {
    public static void verify(int owner) {
        try {
            var method = DismembermentEngine.class.getDeclaredMethod("pieces");
            method.setAccessible(true);
            var pieces = (java.util.List<?>)method.invoke(DismembermentEngine.INSTANCE);
            int count = 0;
            for (Object piece : pieces) if ((int)value(piece,"entityId") == owner) {
                count++;
                var position=(Vec3)value(piece,"position");
                var velocity=(Vec3)value(piece,"velocity");
                var rotation=(org.joml.Quaternionf)value(piece,"orientation");
                if(!Double.isFinite(position.lengthSqr()) || !Double.isFinite(velocity.lengthSqr()) || !rotation.isFinite())
                    throw new AssertionError("Non-finite physics state");
                if(velocity.lengthSqr()>256) throw new AssertionError("Unstable ragdoll velocity");
            }
            if(count<6) throw new AssertionError("Missing body segments: "+count);
        } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static Object value(Object instance,String name) throws ReflectiveOperationException {
        var field=instance.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(instance);
    }
}
