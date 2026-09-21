package net.krodark.asterion.update.underworld.client;

import java.lang.reflect.Method;

/** Optional Iris API bridge: never sample a shader pack's depth as vanilla depth. */
final class ShaderPackCompatibility {
    private static final Object API;
    private static final Method IN_USE;
    private static final boolean UNAVAILABLE;

    static {
        Object api = null;
        Method inUse = null;
        boolean unavailable = false;
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            inUse = type.getMethod("isShaderPackInUse");
        } catch (ClassNotFoundException absent) {
            // Vanilla renderer.
        } catch (ReflectiveOperationException | LinkageError failure) {
            unavailable = true;
        }
        API = api;
        IN_USE = inUse;
        UNAVAILABLE = unavailable;
    }

    private ShaderPackCompatibility() { }

    static boolean active() {
        if (UNAVAILABLE) return true;
        if (IN_USE == null) return false;
        try {
            return Boolean.TRUE.equals(IN_USE.invoke(API));
        } catch (ReflectiveOperationException failure) {
            return true;
        }
    }
}
