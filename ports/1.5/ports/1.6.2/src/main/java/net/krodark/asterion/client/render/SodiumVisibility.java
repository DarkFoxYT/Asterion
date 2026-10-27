package net.krodark.asterion.client.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.krodark.asterion.Asterion;
import net.minecraft.world.phys.Vec3;

/** Optional Sodium 0.9 visibility bridge. Unknown renderers always retain effects. */
public final class SodiumVisibility {
    private static MethodHandle instance, visible;
    private static net.minecraft.client.multiplayer.ClientLevel readyLevel;
    public static void terrainReady() { readyLevel = net.minecraft.client.Minecraft.getInstance().level; }
    public static void reset() { readyLevel = null; }
    static {
        try {
            Class<?> type = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", false,
                    SodiumVisibility.class.getClassLoader());
            var lookup = MethodHandles.publicLookup();
            instance = lookup.findStatic(type, "instanceNullable", MethodType.methodType(type))
                    .asType(MethodType.methodType(Object.class));
            visible = lookup.findVirtual(type, "isBoxVisible", MethodType.methodType(boolean.class,
                    double.class, double.class, double.class, double.class, double.class, double.class))
                    .asType(MethodType.methodType(boolean.class, Object.class,
                            double.class, double.class, double.class, double.class, double.class, double.class));
        } catch (ClassNotFoundException absent) {
            // No hard dependency on Sodium; vanilla and other renderers remain supported.
        } catch (ReflectiveOperationException | LinkageError unsupported) {
            instance = visible = null;
            Asterion.LOGGER.warn("Sodium visibility API unavailable; retaining unculled Amnetic lighting", unsupported);
        }
    }
    private SodiumVisibility() { }
    public static boolean lightVisible(Vec3 center, double radius) {
        if (instance == null || visible == null) return true;
        if (readyLevel == null || readyLevel != net.minecraft.client.Minecraft.getInstance().level) return true;
        try {
            Object renderer = (Object)instance.invokeExact();
            if (renderer == null) return true;
            // Test the complete illumination volume, not just an offscreen lamp's position.
            double r = Math.max(.1, radius) + 2;
            return (boolean)visible.invokeExact(renderer, center.x-r, center.y-r, center.z-r,
                    center.x+r, center.y+r, center.z+r);
        } catch (Throwable unsupported) {
            instance = visible = null;
            Asterion.LOGGER.warn("Sodium visibility bridge disabled; retaining Amnetic lighting", unsupported);
            return true;
        }
    }
}
