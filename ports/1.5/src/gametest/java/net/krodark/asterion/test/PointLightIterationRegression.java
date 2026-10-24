package net.krodark.asterion.test;

import com.meekdev.amnetic.client.light.Light;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.port.client.PortLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.lang.reflect.*;
import java.util.Map;

/** Exercise real lights when visibility hooks refresh or remove lights during culling. */
public final class PointLightIterationRegression {
    public static void run(Minecraft client) {
        var config = AsterionConfig.INSTANCE;
        boolean enabled = config.dynamicLightsEnabled;
        int budget = config.maxDynamicLights;
        try {
            config.dynamicLightsEnabled = true;
            config.maxDynamicLights = 2;
            Class<?> owner = Class.forName("net.krodark.asterion.port.client.PortPointLights");
            Method cull = owner.getDeclaredMethod("cull", Frustum.class);
            cull.setAccessible(true);
            Field field = owner.getDeclaredField("LIGHTS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<Object, Light> lights = (Map<Object, Light>) field.get(null);
            Vec3 position = client.player.position().add(0, 1, 0);
            for (int cycle = 0; cycle < 128; cycle++) {
                PortLight.clear();
                Object first = new Object(), second = new Object(), third = new Object();
                update(first, position);
                update(second, position);
                if (lights.size() != 2) throw new AssertionError("Compute lights unavailable in regression fixture");
                Frustum refreshing = new Frustum(new Matrix4f(), new Matrix4f()) {
                    boolean refreshed;
                    @Override public boolean isVisible(AABB box) {
                        if (!refreshed) { refreshed = true; update(first, position); }
                        return true;
                    }
                };
                cull.invoke(null, refreshing);
                if (lights.values().stream().anyMatch(light -> !light.isEnabled()))
                    throw new AssertionError("Visible lights lost after reentrant refresh");
                update(third, position);
                if (lights.size() != 2 || !lights.containsKey(first) || !lights.containsKey(third))
                    throw new AssertionError("LRU light budget changed");
                Light old = lights.get(first);
                Frustum clearing = new Frustum(new Matrix4f(), new Matrix4f()) {
                    @Override public boolean isVisible(AABB box) {
                        PortLight.clear();
                        return true;
                    }
                };
                cull.invoke(null, clearing);
                if (!lights.isEmpty() || old.isEnabled())
                    throw new AssertionError("Cleared light survived culling");
            }
            Asterion.LOGGER.info("ASTERION_POINT_LIGHTS PASS: 128 reentrant refresh/clear cycles, visibility and LRU preserved");
        } catch (InvocationTargetException failure) {
            throw new AssertionError("Point-light culling failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        } finally {
            PortLight.clear();
            config.dynamicLightsEnabled = enabled;
            config.maxDynamicLights = budget;
        }
    }

    private static void update(Object key, Vec3 position) {
        PortLight.updateItemGlowLight(key, position, .18F, 1F, .30F, 2.15F, 9F, false);
    }
}
