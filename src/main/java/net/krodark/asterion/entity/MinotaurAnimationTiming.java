package net.krodark.asterion.entity;

 
public final class MinotaurAnimationTiming {
    public static final double WALK_LENGTH = 2.439, RUN_LENGTH = 1.343;

    /** Contact times use the authored 24 fps timeline, including loop boundaries. */
    public static boolean crossedFootstep(double previous, double current, boolean walking) {
        double length = walking ? WALK_LENGTH : RUN_LENGTH;
        double first = (walking ? 29 : 18) / 24.0;
        double second = (walking ? 58 : 32) / 24.0;
        return Math.floor((current - first) / length) > Math.floor((previous - first) / length)
                || Math.floor((current - second) / length) > Math.floor((previous - second) / length);
    }
     
    public static final double ROAR_SOUND_SECONDS = 66.0 / 24.0;
    public static final int AXE_RELEASE = 15;
    public static final int[] COMBO_HITS = {19, 33, 46};
     
    public static final int[] SWORD_COMBO_HITS = {18, 42};
     
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
     
    public static final int ENTRY_CAMERA_TICKS = 50;
    public static final int ENTRY_BREAK_TICK = ENTRY_CAMERA_TICKS + 112;
    public static final int ENTRY_WALK_END_TICK = ENTRY_BREAK_TICK + 49;
    public static final int ENTRY_END_TICK = ENTRY_WALK_END_TICK + 140;
    public static final float ENTRY_ROAR_PITCH = 1.15F;
    // Walk one full gait cycle, then plant the feet before the faster roar.
    // The six-second clip starts at frame 66 and finishes with the animation.
    public static final Track ENTRY_ROAR = new Track(
            new double[]{0, ENTRY_WALK_END_TICK, ENTRY_WALK_END_TICK + 36, ENTRY_END_TICK},
            new double[]{0, 0, ROAR_SOUND_SECONDS, 7.4713});
    public static double entryWalkDistance(double tick, double distance) {
        double t = Math.clamp((tick - ENTRY_BREAK_TICK) / 49.0, 0, 1);
        // Constant stride through the doorway with short acceleration/deceleration ramps.
        double ramp = .12;
        double area = t < ramp ? t * t / (2 * ramp)
                : t > 1 - ramp ? 1 - ramp - (1 - t) * (1 - t) / (2 * ramp)
                : t - ramp / 2;
        return distance * area / (1 - ramp);
    }
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
            throw new IllegalStateException("Roar clip does not reach frame 66");
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
