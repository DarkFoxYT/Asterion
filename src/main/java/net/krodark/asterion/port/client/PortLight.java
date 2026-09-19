package net.krodark.asterion.port.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class PortLight {
    private static net.minecraft.client.multiplayer.ClientLevel lightWorld;
    private static final Map<Object, Long> UPDATED = new HashMap<>();

    private PortLight() {}

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
        PortPointLights.update(key,
                new LedPointLightSample(position, red, green, blue, strength, radius, castsShadow));
    }

    public static void tickCleanup(Minecraft client) {
        if (client.level != lightWorld) {
            clear();
            lightWorld = client.level;
        }
        if (client.level == null) return;


        long stale = client.level.getGameTime() - 40L;
        Iterator<Map.Entry<Object, Long>> iterator = UPDATED.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Long> entry = iterator.next();
            if (entry.getValue() < stale || !PortPointLights.loaded(entry.getKey(), client.level)) {
                PortPointLights.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    public static void clear() {
        UPDATED.clear();
        PortPointLights.clear();
        PortHeldItemLights.clear();
    }

    public static void removeItemGlowLight(Object key) {
        if(key==null)return;
        UPDATED.remove(key);
        PortPointLights.remove(key);
    }

    public static RenderType bloomRenderLayer(ResourceLocation texture) {
        return PortEmissiveBuffer.renderType(texture);
    }


    public static Vec3 nearestAttractor(Vec3 origin, double maxDistance) {
        return origin == null ? null : PortPointLights.nearestAttractor(origin, maxDistance);
    }

    public record LedPointLightSample(Vec3 position, float red, float green, float blue,
                                      float strength, float radius, boolean castsShadow) {
        public LedPointLightSample(Vec3 position, float red, float green, float blue,
                                   float strength, float radius) {
            this(position, red, green, blue, strength, radius, true);
        }
    }
}

