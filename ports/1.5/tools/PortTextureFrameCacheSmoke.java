import com.mojang.blaze3d.platform.NativeImage;
import net.krodark.asterion.port.client.PortTextureFrameCache;

public final class PortTextureFrameCacheSmoke {
    public static void main(String[] args) {
        long mb = 1024L * 1024;
        Object first = new Object(), active = new Object();
        try (NativeImage source = new NativeImage(512, 512, false)) {
            NativeImage[] image = { source };
            for (int frame = 0; frame < 12; frame++) PortTextureFrameCache.put(first, source, frame, image);
            if (PortTextureFrameCache.usedBytes() != 8 * mb)
                throw new AssertionError("One animation monopolized the entire budget");
            var firstFrame = PortTextureFrameCache.get(first, source, 0);
            for (int frame = 8; frame < 40; frame++) {
                PortTextureFrameCache.get(first, source, frame);
                PortTextureFrameCache.put(first, source, frame, image);
            }
            if (PortTextureFrameCache.get(first, source, 0) != firstFrame)
                throw new AssertionError("Oversized animation churned its admitted frames");
            long cutoff = System.nanoTime();
            PortTextureFrameCache.put(active, source, 0, image);
            var activeFrame = PortTextureFrameCache.get(active, source, 0);
            PortTextureFrameCache.trimIdle(cutoff + 15_000_000_000L);
            if (PortTextureFrameCache.usedBytes() != mb || PortTextureFrameCache.get(first, source, 0) != null
                    || PortTextureFrameCache.get(active, source, 0) != activeFrame)
                throw new AssertionError("Idle cleanup did not preserve active frames");
            for (int owner = 0; owner < 5; owner++) {
                Object key = new Object();
                for (int frame = 0; frame < 8; frame++) PortTextureFrameCache.put(key, source, frame, image);
            }
            if (PortTextureFrameCache.usedBytes() != 32 * mb) throw new AssertionError("Shared budget exceeded");
            PortTextureFrameCache.tick(new Object());
            if (PortTextureFrameCache.usedBytes() != 0) throw new AssertionError("World change retained frames");
            for (int world = 0; world < 50; world++) {
                PortTextureFrameCache.put(first, source, 0, image);
                PortTextureFrameCache.clear(first);
                PortTextureFrameCache.clear(first);
                PortTextureFrameCache.put(active, source, 0, image);
                PortTextureFrameCache.tick(world % 2 == 0 ? new Object() : null);
                if (PortTextureFrameCache.usedBytes() != 0) throw new AssertionError("Transition leaked memory");
            }
            PortTextureFrameCache.clearAll();
            // Source images belong to the texture, not the optional frame cache.
            source.setPixelRGBA(0, 0, 0xFF123456);
            if (source.getPixelRGBA(0, 0) != 0xFF123456) throw new AssertionError("Source image was released");
        }
        System.out.println("PASS native frames: per-animation/shared caps, stable hits, idle cleanup, 50 world changes, source ownership");
    }
}
