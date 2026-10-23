package net.krodark.asterion.port.client;

/** Release owned render caches at renderer reload/disconnect/shutdown boundaries. */
public final class PortRenderCaches {
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private PortRenderCaches() { }
    public static void tick(net.minecraft.client.multiplayer.ClientLevel current) {
        if (level != current) {
            clear();
            level = current;
        }
    }
    public static void clear() {
        PortEmissiveQueue.clearCaches();
        PortEmissiveBuffer.clearCaches();
        PortPostBuffers.clearCaches();
        PortTextureFrameCache.clearAll();
        PortDimensionEffects.clearCameraCache();
        com.meekdev.amnetic.client.pipeline.internal.LayerSystem.INSTANCE.dispose();
    }
}
