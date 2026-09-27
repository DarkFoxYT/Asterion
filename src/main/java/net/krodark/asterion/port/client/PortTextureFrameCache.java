package net.krodark.asterion.port.client;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.LinkedHashMap;

/** Exact interpolated pixels, shared bounded budget, released on texture close/reload. */
public final class PortTextureFrameCache {
    private record Key(Object owner, Object source, long frame) {}
    private static final LinkedHashMap<Key, NativeImage[]> FRAMES = new LinkedHashMap<>(64, .75F, true);
    private static final long LIMIT = 32L * 1024 * 1024;
    private static long bytes;
    private PortTextureFrameCache() {}
    public static NativeImage[] get(Object owner, Object source, long frame) { return FRAMES.get(new Key(owner, source, frame)); }
    public static void put(Object owner, Object source, long frame, NativeImage[] images) {
        long size = 0;
        for (var image : images) size += (long)image.getWidth() * image.getHeight() * 4;
        if (size > LIMIT || FRAMES.containsKey(new Key(owner, source, frame))) return;
        // Keep admitted frames until reload: a cycling animation larger than
        // the budget would otherwise evict and recopy every frame forever.
        if (bytes + size > LIMIT) return;
        NativeImage[] copy = new NativeImage[images.length];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = new NativeImage(images[i].getWidth(), images[i].getHeight(), false);
            copy[i].copyFrom(images[i]);
        }
        FRAMES.put(new Key(owner, source, frame), copy); bytes += size;
    }
    public static void clear(Object owner) {
        var iterator = FRAMES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().owner == owner) { release(entry.getValue()); iterator.remove(); }
        }
    }
    private static void release(NativeImage[] images) {
        for (var image : images) { bytes -= (long)image.getWidth() * image.getHeight() * 4; image.close(); }
    }
}
