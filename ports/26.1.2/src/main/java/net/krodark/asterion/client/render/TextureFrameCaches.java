package net.krodark.asterion.client.render;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ClientLevel;

/** Own only the optional frame copies: source textures can repopulate lazily after a transition. */
public final class TextureFrameCaches {
    private static final Set<TextureCacheOwner> OWNERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static ClientLevel level;
    private TextureFrameCaches() { }
    public static synchronized void track(TextureCacheOwner owner) { OWNERS.add(owner); }
    public static synchronized void releaseAll() {
        for (TextureCacheOwner owner : java.util.List.copyOf(OWNERS)) owner.asterion$releaseFrameCache();
        OWNERS.clear();
    }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (level != client.level) { releaseAll(); level = client.level; }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { releaseAll(); level = null; });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { releaseAll(); level = null; });
    }
}
