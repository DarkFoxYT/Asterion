package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.Map;

/** Per-render-state storage, owned by the native mixin and released with it. */
public interface FabricRenderState {
    Map<RenderStateDataKey<?>, Object> asterion$nativeData(boolean create);

    @SuppressWarnings("unchecked")
    default <T> T getData(RenderStateDataKey<T> key) {
        var data = asterion$nativeData(false);
        return data == null ? null : (T) data.get(key);
    }

    default <T> T getDataOrDefault(RenderStateDataKey<T> key, T fallback) {
        var value = getData(key);
        return value == null ? fallback : value;
    }

    default <T> void setData(RenderStateDataKey<T> key, T value) {
        var data = asterion$nativeData(value != null);
        if (data != null) {
            if (value == null) data.remove(key);
            else data.put(key, value);
        }
    }

    default void clearExtraData() {
        var data = asterion$nativeData(false);
        if (data != null) data.clear();
    }
}
