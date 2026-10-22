package net.krodark.asterion.port.client;

import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import java.lang.reflect.Method;

/** Optional shader APIs; never change the player's selected pack or saved settings. */
public final class ShaderCompatibility {
    private static final Probe IRIS = iris();
    private static final Probe OPTIFINE = optifine();
    private ShaderCompatibility() {}

    public static boolean externalShadersActive() {
        var level = Minecraft.getInstance().level;
        if (level != null && level.dimension().equals(Asterion.ASTERION_LEVEL)) return false;
        return IRIS.active() || OPTIFINE.active();
    }

    private static Probe iris() {
        for (String prefix : new String[]{"net.irisshaders.iris", "net.coderbot.iris"}) {
            try {
                Class<?> type = Class.forName(prefix + ".api.v0.IrisApi");
                return new Probe(type.getMethod("getInstance").invoke(null), type.getMethod("isShaderPackInUse"));
            } catch (ClassNotFoundException absent) { }
            catch (ReflectiveOperationException failure) { return new Probe(null, null, true); }
        }
        return new Probe(null, null);
    }

    private static Probe optifine() {
        try { return new Probe(null, Class.forName("net.optifine.Config").getMethod("isShaders")); }
        catch (ClassNotFoundException absent) { return new Probe(null, null); }
        catch (ReflectiveOperationException failure) { return new Probe(null, null, true); }
    }

    private record Probe(Object owner, Method query, boolean fallback) {
        Probe(Object owner, Method query) { this(owner, query, false); }
        boolean active() {
            if (query == null) return fallback;
            try { return (Boolean) query.invoke(owner); }
            catch (ReflectiveOperationException failure) { return true; }
        }
    }
}
