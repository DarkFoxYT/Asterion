package net.krodark.asterion.test;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.*;
import java.util.*;

/** Repeatedly seed stale-world references, then exercise the same cleanup used by client ticks. */
final class RenderCacheLifecycleRegression {
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void run(Minecraft client) {
        String base = "net.krodark.asterion.client.render.entity.";
        try {
            for (String suffix : List.of("WeaponChainAttachments", "MinotaurHandAttachment")) {
                Class<?> type = Class.forName(base + suffix);
                var tick = type.getDeclaredMethod("tick", net.minecraft.client.multiplayer.ClientLevel.class);
                tick.setAccessible(true);
                for (var field : type.getDeclaredFields()) {
                    if (!Map.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    Map map = (Map)field.get(null);
                    Class<?> valueType = (Class<?>)((ParameterizedType)field.getGenericType()).getActualTypeArguments()[1];
                    var constructor = valueType.getDeclaredConstructor(Object.class, long.class, Vec3.class);
                    constructor.setAccessible(true);
                    for (int cycle = 0; cycle < 1000; cycle++) {
                        Object oldWorld = new Object();
                        map.put(42, constructor.newInstance(oldWorld, 0L, Vec3.ZERO));
                        tick.invoke(null, client.level);
                        if (!map.isEmpty()) throw new AssertionError("Stale world retained by " + field);
                        // Valid same-world entries survive; disconnect releases them without another render.
                        map.put(42, constructor.newInstance(client.level, client.level.getGameTime(), Vec3.ZERO));
                        tick.invoke(null, client.level);
                        if (map.size() != 1) throw new AssertionError("Current animation removed by " + field);
                        tick.invoke(null, new Object[]{null});
                        if (!map.isEmpty()) throw new AssertionError("Disconnected world retained by " + field);
                    }
                }
            }
            var engine = net.krodark.asterion.client.ragdoll.DismembermentEngine.INSTANCE;
            for (var field : engine.getClass().getDeclaredFields()) {
                if (!(field.getName().startsWith("tick") || field.getName().startsWith("selfCollision"))) continue;
                field.setAccessible(true);
                Object value = field.get(engine);
                if (value instanceof Map map) map.put(new Object(), new Object());
                else if (value instanceof Collection collection) collection.add(new Object());
            }
            engine.clear();
            for (var field : engine.getClass().getDeclaredFields()) {
                if (!(field.getName().startsWith("tick") || field.getName().startsWith("selfCollision"))) continue;
                field.setAccessible(true);
                Object value = field.get(engine);
                if (value instanceof Map map && !map.isEmpty() || value instanceof Collection collection && !collection.isEmpty())
                    throw new AssertionError("Physics scratch data retained by " + field);
            }
            net.krodark.asterion.Asterion.LOGGER.info("ASTERION_PERF PASS ragdoll scratch collections released on clear");
            net.krodark.asterion.Asterion.LOGGER.info("ASTERION_PERF PASS 3000 stale-world/current-world/disconnect attachment cache cycles");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
