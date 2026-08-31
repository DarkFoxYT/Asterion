package net.krodark.asterion.client;

import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.Minecraft;

/** Slow, hysteretic quality governor driven by actual rendered frame time. */
public final class PerformanceGovernor {
    private static long previousFrame;
    private static double frameMillis = 6.0;
    private static int quality = 1;
    private static int slowFrames, fastFrames;

    private PerformanceGovernor() { }

    public static void frame(Minecraft client) {
        long now=System.nanoTime();
        if(previousFrame==0L){previousFrame=now;return;}
        double elapsed=Math.min(100.0,(now-previousFrame)/1_000_000.0);
        previousFrame=now;
        if(client.level==null || !AsterionConfig.INSTANCE.adaptivePerformance) {
            frameMillis=elapsed;quality=2;slowFrames=fastFrames=0;return;
        }
        frameMillis += (elapsed-frameMillis)*0.045;
        double target=1000.0/AsterionConfig.INSTANCE.performanceTargetFps;
        int wanted=frameMillis>target*1.24 ? 0 : frameMillis>target*.86 ? 1 : 2;
        if(wanted<quality) {
            fastFrames=0;
            if(++slowFrames>=45){quality--;slowFrames=0;}
        } else if(wanted>quality) {
            slowFrames=0;
            // Upgrades are deliberately slow, preventing alternating long/short frames.
            if(++fastFrames>=600){quality++;fastFrames=0;}
        } else slowFrames=fastFrames=0;
    }

    public static int quality() {
        return AsterionConfig.INSTANCE.adaptivePerformance ? quality : 2;
    }

    public static double frameMillis() { return frameMillis; }
}
