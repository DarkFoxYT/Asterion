package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Forge-side equivalent of Fabric's extra entity render state data. */
public interface FabricRenderState {
    Map<Object, Map<RenderStateDataKey<?>, Object>> EXTRA = Collections.synchronizedMap(new WeakHashMap<>());

    @SuppressWarnings("unchecked")
    default <T> T getData(RenderStateDataKey<T> key) {
        Map<RenderStateDataKey<?>, Object> data = EXTRA.get(this);
        return data == null ? null : (T) data.get(key);
    }

    default <T> T getDataOrDefault(RenderStateDataKey<T> key, T fallback) {
        T value = getData(key);
        return value == null ? fallback : value;
    }

    default <T> void setData(RenderStateDataKey<T> key, T value) {
        if (value == null) {
            Map<RenderStateDataKey<?>, Object> data = EXTRA.get(this);
            if (data != null) data.remove(key);
        } else {
            EXTRA.computeIfAbsent(this, ignored -> new IdentityHashMap<>()).put(key, value);
        }
    }

    default void clearExtraData() {
        EXTRA.remove(this);
    }
}
