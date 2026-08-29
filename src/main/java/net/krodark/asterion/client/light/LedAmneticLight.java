package net.krodark.asterion.client.light;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class LedAmneticLight {
    private static final Map<Object, Long> UPDATED = new HashMap<>();

    private LedAmneticLight() {}

    public static void updateItemGlowLight(Object key, Vec3 position, float red, float green,
                                           float blue, float strength, float radius) {
        updateItemGlowLight(key, position, red, green, blue, strength, radius, true);
    }

    public static void updateItemGlowLight(Object key, Vec3 position, float red, float green,
                                           float blue, float strength, float radius,
                                           boolean castsShadow) {
        Minecraft client = Minecraft.getInstance();
        if (key == null || client.level == null) {
            return;
        }
        UPDATED.put(key, client.level.getGameTime());
        LedAmneticPointLights.update(key,
                new LedPointLightSample(position, red, green, blue, strength, radius, castsShadow));
    }

    public static void tickCleanup(Minecraft client) {
        if (client.level == null) {
            UPDATED.clear();
            LedAmneticPointLights.clear();
            return;
        }
        long stale = client.level.getGameTime() - 1L;
        Iterator<Map.Entry<Object, Long>> iterator = UPDATED.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Long> entry = iterator.next();
            if (entry.getValue() < stale) {
                LedAmneticPointLights.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    public static void removeItemGlowLight(Object key) {
        if(key==null)return;
        UPDATED.remove(key);
        LedAmneticPointLights.remove(key);
    }

    public static RenderType bloomRenderLayer(Identifier texture) {
        return AsterionEmissiveBuffer.renderType(texture);
    }

    /** Returns the strongest useful nearby live light for light-seeking ambient creatures. */
    public static Vec3 nearestAttractor(Vec3 origin, double maxDistance) {
        return origin == null ? null : LedAmneticPointLights.nearestAttractor(origin, maxDistance);
    }

    public record LedPointLightSample(Vec3 position, float red, float green, float blue,
                                      float strength, float radius, boolean castsShadow) {
        public LedPointLightSample(Vec3 position, float red, float green, float blue,
                                   float strength, float radius) {
            this(position, red, green, blue, strength, radius, true);
        }
    }
}

