package net.krodark.asterion.port.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Cache exact interpolation results while their animation is active; own only copied pixels. */
public final class PortTextureFrameCache {
    private record Key(Object source, long frame) { }
    private static final class OwnerFrames {
        final Map<Key, NativeImage[]> frames = new HashMap<>();
        long bytes;
        long lastUse;
    }
    private static final Map<Object, OwnerFrames> OWNERS = new IdentityHashMap<>();
    private static final long LIMIT = 32L * 1024 * 1024;
    private static final long OWNER_LIMIT = 8L * 1024 * 1024;
    private static final long IDLE_NANOS = 15_000_000_000L;
    private static long bytes;
    private static Object level;
    private static int sweepTicks;
    private PortTextureFrameCache() { }

    public static NativeImage[] get(Object owner, Object source, long frame) {
        OwnerFrames entry = OWNERS.get(owner);
        if (entry == null) return null;
        // A cache miss still means this animation is active; do not evict partial cycles.
        entry.lastUse = System.nanoTime();
        return entry.frames.get(new Key(source, frame));
    }

    public static void put(Object owner, Object source, long frame, NativeImage[] images) {
        long size = 0;
        for (var image : images) size += (long)image.getWidth() * image.getHeight() * 4;
        OwnerFrames entry = OWNERS.get(owner);
        Key key = new Key(source, frame);
        if (entry != null && entry.frames.containsKey(key)) return;
        // Keep active admitted frames stable. Evicting them on each miss causes allocation churn.
        if (size <= 0 || size > OWNER_LIMIT || bytes + size > LIMIT
                || entry != null && entry.bytes + size > OWNER_LIMIT) return;
        NativeImage[] copy = new NativeImage[images.length];
        try {
            for (int i = 0; i < copy.length; i++) {
                copy[i] = new NativeImage(images[i].getWidth(), images[i].getHeight(), false);
                copy[i].copyFrom(images[i]);
            }
            if (entry == null) {
                entry = new OwnerFrames();
                OWNERS.put(owner, entry);
            }
            entry.frames.put(key, copy);
            entry.bytes += size;
            entry.lastUse = System.nanoTime();
            bytes += size;
        } catch (RuntimeException | Error failure) {
            for (var image : copy) if (image != null) image.close();
            throw failure;
        }
    }

    public static void tick(Object currentLevel) {
        if (level != currentLevel) { clearAll(); level = currentLevel; }
        if (++sweepTicks >= 20) { sweepTicks = 0; trimIdle(System.nanoTime()); }
    }

    public static void trimIdle(long now) {
        var iterator = OWNERS.values().iterator();
        while (iterator.hasNext()) {
            OwnerFrames entry = iterator.next();
            if (now - entry.lastUse >= IDLE_NANOS) { release(entry); iterator.remove(); }
        }
    }

    public static void clear(Object owner) {
        OwnerFrames entry = OWNERS.remove(owner);
        if (entry != null) release(entry);
    }

    public static void clearAll() {
        OWNERS.values().forEach(PortTextureFrameCache::release);
        OWNERS.clear();
    }

    public static long usedBytes() { return bytes; }

    private static void release(OwnerFrames entry) {
        for (var frames : entry.frames.values()) for (var image : frames) image.close();
        bytes -= entry.bytes;
        entry.frames.clear();
        entry.bytes = 0;
    }
}
