package net.krodark.asterion.client.render;

import java.util.Collections;
import java.util.Set;
import java.util.IdentityHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ClientLevel;

/** Own only the optional frame copies: source textures can repopulate lazily after a transition. */
public final class TextureFrameCaches {
    private static final Set<TextureCacheOwner> OWNERS = Collections.newSetFromMap(new IdentityHashMap<>());
    // Keep owners alive until their native allocations and budget reservations are released.
    private static final long IDLE_NANOS = 15_000_000_000L;
    private static ClientLevel level;
    private static int sweepTicks;
    private TextureFrameCaches() { }
    public static synchronized void track(TextureCacheOwner owner) { OWNERS.add(owner); }
    public static synchronized void forget(TextureCacheOwner owner) { OWNERS.remove(owner); }
    public static synchronized void trimIdle(long now) {
        for (TextureCacheOwner owner : java.util.List.copyOf(OWNERS)) {
            if (now - owner.asterion$lastUse() >= IDLE_NANOS) {
                owner.asterion$releaseFrameCache();
                OWNERS.remove(owner);
            }
        }
    }
    public static synchronized void releaseAll() {
        for (TextureCacheOwner owner : java.util.List.copyOf(OWNERS)) owner.asterion$releaseFrameCache();
        OWNERS.clear();
    }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (level != client.level) { releaseAll(); level = client.level; }
            if (++sweepTicks >= 20) { sweepTicks = 0; trimIdle(System.nanoTime()); }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> { releaseAll(); level = null; }));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { releaseAll(); level = null; });
    }
}
