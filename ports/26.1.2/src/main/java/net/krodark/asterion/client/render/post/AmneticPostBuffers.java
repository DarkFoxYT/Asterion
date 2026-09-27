package net.krodark.asterion.client.render.post;

import com.meekdev.amnetic.client.post.PostEffectConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import java.util.HashMap;
import java.util.Map;

/** Reusable, depth-free intermediates supplied through Amnetic's external framebuffer API. */
public final class AmneticPostBuffers {
    private record Buffer(RenderTarget target, long used) { }
    private static final Map<String, Buffer> BUFFERS = new HashMap<>();
    private static boolean initialized;
    private static net.minecraft.client.multiplayer.ClientLevel trackedLevel;
    private AmneticPostBuffers() { }

    public static PostEffectConfig attach(PostEffectConfig config, String name, float scale) {
        return attach(config, name, () -> scale);
    }

    public static PostEffectConfig attach(PostEffectConfig config, String name, java.util.function.DoubleSupplier scale) {
        if (!initialized) {
            initialized = true;
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (trackedLevel != client.level) {
                    BUFFERS.values().forEach(buffer -> buffer.target.destroyBuffers());
                    BUFFERS.clear();
                    trackedLevel = client.level;
                }
                long now = System.nanoTime();
                BUFFERS.entrySet().removeIf(entry -> {
                    if (client.level != null && now - entry.getValue().used < 2_000_000_000L) return false;
                    entry.getValue().target.destroyBuffers();
                    return true;
                });
            });
        }
        // Amnetic PostEffects consumes RenderTarget suppliers (its standalone Framebuffer is a separate API).
        return config.externalTarget(Asterion.id(name), () -> get(name, (float)scale.getAsDouble()));
    }

    private static RenderTarget get(String name, float scale) {
        var main = Minecraft.getInstance().getMainRenderTarget();
        int width = Math.max(1, Math.round(main.width * scale));
        int height = Math.max(1, Math.round(main.height * scale));
        Buffer cached = BUFFERS.get(name);
        RenderTarget target = cached == null ? new TextureTarget("asterion/" + name, width, height, false) : cached.target;
        if (target.width != width || target.height != height) target.resize(width, height);
        BUFFERS.put(name, new Buffer(target, System.nanoTime()));
        return target;
    }
}
