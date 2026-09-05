package net.krodark.asterion.client.render;

/** Releases native frame buffers before a texture is reloaded or closed. */
public interface TextureCacheOwner {
    void asterion$setFrameCleanup(Runnable cleanup);
}
