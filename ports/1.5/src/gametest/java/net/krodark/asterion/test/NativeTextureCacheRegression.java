package net.krodark.asterion.test;

import com.mojang.blaze3d.platform.NativeImage;
import net.krodark.asterion.port.client.PortTextureFrameCache;

final class NativeTextureCacheRegression {
    static void run() {
        PortTextureFrameCache.clearAll();
        try (var source = new NativeImage(256, 256, false)) {
            source.setPixelRGBA(7, 9, 0x7f12ab34);
            for (int cycle = 0; cycle < 3; cycle++) {
                for (int owner = 0; owner < 8; owner++) {
                    Object key = new Object();
                    for (int frame = 0; frame < 40; frame++) {
                        PortTextureFrameCache.put(key, source, frame, new NativeImage[]{source});
                        if (PortTextureFrameCache.usedBytes() > 32L * 1024 * 1024)
                            throw new AssertionError("Native frame cache exceeds aggregate budget");
                        var cached = PortTextureFrameCache.get(key, source, frame);
                        if (cached != null && cached[0].getPixelRGBA(7, 9) != 0x7f12ab34)
                            throw new AssertionError("Cached texture pixels changed");
                    }
                }
                if (PortTextureFrameCache.usedBytes() != 32L * 1024 * 1024)
                    throw new AssertionError("Budget fixture did not fill the cache");
                PortTextureFrameCache.trimIdle(System.nanoTime() + 16_000_000_000L);
                PortTextureFrameCache.clearAll();
                PortTextureFrameCache.clearAll();
                if (PortTextureFrameCache.usedBytes() != 0) throw new AssertionError("Native frame allocations retained");
            }
        }
        net.krodark.asterion.Asterion.LOGGER.info("ASTERION_PERF PASS native frame cache: 32MiB cap, exact pixels, idle cleanup, repeated release and reuse");
    }
}
