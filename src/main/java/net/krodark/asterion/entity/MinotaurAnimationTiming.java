package net.krodark.asterion.entity;

/** Maps server action ticks to authored keyframes, including release and recovery rather than only clip length. */
public final class MinotaurAnimationTiming {
    // Authored animation frames are 24 fps, independently of Minecraft's 20 server ticks/sec.
    public static final double ROAR_SOUND_SECONDS = 60.0 / 24.0;
    public static final int AXE_RELEASE = 15;
    public static final int[] COMBO_HITS = {19, 33, 46};
    /** Server contacts mapped exactly to authored frames 22 and 50 at 24 FPS. */
    public static final int[] SWORD_COMBO_HITS = {18, 42};
    /** Authored frame 30 (1.25 seconds) on punch_single. */
    public static final int PUNCH_SINGLE_HIT = 25;
    public static final Track CLEAVE = track(48, 2.3864, 18, .9091);
    public static final Track CHOP = track(40, 1.9583, 26, 1.25);
    public static final Track SLAM = track(44, 1.9583, 26, 1.25);
    public static final Track COMBO = new Track(new double[]{0, 19, 33, 46, 73},
            new double[]{0, .9583, 1.6667, 2.2917, 3.625});
    public static final Track SWORD_COMBO = new Track(new double[]{0, 18, 42, 73},
            new double[]{0, 22.0 / 24.0, 50.0 / 24.0, 3.625});
    public static final Track SPIN = track(36, 1.8, 19, .95);
    public static final Track THROW = track(30, 1.25, AXE_RELEASE, .7083);
    public static final Track RUBBLE = track(58, 1.9583, 26, 1.2917);
    public static final Track CHAIN = track(36, 1.7917, 25, 1.25);
    public static final Track ARROWS = track(38, 1.7917, 20, 1.25);
    public static final Track PUNCH = track(40, 1.9583, PUNCH_SINGLE_HIT, 30.0 / 24.0);
    public static final Track BACK_KICK = track(30, 1.5, 15, .75);
    public static final Track DRAW_SWORD = track(34, 1.7083);
    public static final Track DRAW_AXE = track(24, 1.0);
    public static final Track SHEATHE_SWORD = track(24, 1.5417);
    public static final Track SHEATHE_AXE = track(20, 1.0);
    public static final Track ROAR = track(150, 7.4713);
    // Door impact coincides with the jaw opening; the sustained roar plays after the entrance advance.
    public static final Track ENTRY_ROAR = new Track(new double[]{0, 52, 70, 100, 138, 150},
            new double[]{0, 2.2989, 3.0172, 3.8793, 6.8966, 7.4713});
    public static final Track FIRE_ROAR = new Track(new double[]{0, 18, 24, 78, 92, 108},
            new double[]{0, 2.5862, 3.0172, 5.364, 6.1303, 7.4713});
    public static final Track BELCH = track(65, 3.25);
    public static final Track LEAP = track(20, .9703);
    public static final Track LAND = track(12, .9703);
    public static final Track DIES = track(85, 2.9583);
    public static final Track REVIVE = track(30, .6667);
    private MinotaurAnimationTiming() { }

    public static double chargeSeconds(double tick, int windup) {
        return Math.clamp(tick / Math.max(1, windup), 0, 1) * 3.4849;
    }
    private static Track track(int end, double length, double... events) {
        double[] ticks = new double[events.length / 2 + 2], frames = new double[ticks.length];
        for (int i = 0; i < events.length / 2; i++) { ticks[i + 1] = events[i * 2]; frames[i + 1] = events[i * 2 + 1]; }
        ticks[ticks.length - 1] = end; frames[frames.length - 1] = length;
        return new Track(ticks, frames);
    }
    public static final class Track {
        private final double[] ticks, frames;
        private Track(double[] ticks, double[] frames) { this.ticks = ticks; this.frames = frames; }
        public int roarSoundTick() {
            for (int tick = 1; tick <= ticks[ticks.length - 1]; tick++)
                if (seconds(tick) >= ROAR_SOUND_SECONDS) return tick;
            throw new IllegalStateException("Roar clip does not reach frame 60");
        }
        public double seconds(double tick) {
            if (tick <= 0) return 0;
            for (int i = 1; i < ticks.length; i++) if (tick <= ticks[i]) {
                double alpha = (tick - ticks[i - 1]) / (ticks[i] - ticks[i - 1]);
                return frames[i - 1] + alpha * (frames[i] - frames[i - 1]);
            }
            return frames[frames.length - 1];
        }
    }
}
