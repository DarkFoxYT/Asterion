package net.krodark.asterion.client.render;

/** Shared native-memory ceiling: discovering more textures cannot multiply the cache limit. */
public final class TextureFrameBudget {
    private static final long LIMIT = 32L * 1024 * 1024;
    private static long used;
    private TextureFrameBudget() { }
    public static synchronized boolean reserve(long bytes) {
        if (bytes <= 0 || bytes > LIMIT - used) return false;
        used += bytes;
        return true;
    }
    public static synchronized void release(long bytes) { used = Math.max(0, used - bytes); }
    public static synchronized long usedBytes() { return used; }
}
