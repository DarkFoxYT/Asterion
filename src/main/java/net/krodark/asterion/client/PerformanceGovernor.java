package net.krodark.asterion.client;

import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.Minecraft;

public final class PerformanceGovernor {
    private static long previousFrame;
    private static double frameMillis = 16.0;
    private static int quality = 1;
    private static double slowMillis, fastMillis;

    private PerformanceGovernor() { }

    public static void frame(Minecraft client) {
        long now = System.nanoTime();
        if (client.level == null || client.isPaused() || !AsterionConfig.INSTANCE.adaptivePerformance) {
            previousFrame = 0L;
            slowMillis = fastMillis = 0;
            quality = 1;
            return;
        }
        if (previousFrame == 0L) {
            previousFrame = now;
            frameMillis = 1000.0 / AsterionConfig.INSTANCE.performanceTargetFps;
            return;
        }
        double elapsed = (now - previousFrame) / 1_000_000.0;
        previousFrame = now;
        // Loading or switching windows is not a sustained rendering bottleneck.
        if (elapsed > 1000.0) {
            slowMillis = fastMillis = 0;
            return;
        }
        elapsed = Math.min(100.0, elapsed);
        frameMillis += (elapsed - frameMillis) * (1.0 - Math.exp(-elapsed / 350.0));
        int targetFps = Math.min(AsterionConfig.INSTANCE.performanceTargetFps,
                Math.max(1, client.options.framerateLimit().get()));
        double target = 1000.0 / targetFps;
        // Leave headroom for frame caps: meeting the target must not lower quality.
        if (frameMillis > target * 1.20) {
            fastMillis = 0;
            slowMillis += elapsed;
            if (slowMillis >= 1500.0) {
                quality = Math.max(0, quality - 1);
                slowMillis = 0;
            }
        } else if (frameMillis < target * 1.05) {
            slowMillis = 0;
            fastMillis += elapsed;
            if (fastMillis >= 15000.0) {
                quality = Math.min(2, quality + 1);
                fastMillis = 0;
            }
        } else {
            slowMillis = fastMillis = 0;
        }
    }

    public static int quality() {
        return AsterionConfig.INSTANCE.adaptivePerformance ? quality : 2;
    }

    public static double frameMillis() { return frameMillis; }
}
