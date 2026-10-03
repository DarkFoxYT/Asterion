package net.krodark.asterion.game;

/** Shared beats for the server collapse and the client Dead Sun choreography. */
public final class FinaleTimeline {
    public static final int BEAM_START = 96;
    public static final int BEAM_END = 174;
    public static final int IMPLOSION_START = 174;
    public static final int DETONATION = 220;
    public static final int BLACKOUT_END = 265;
    public static final int RETURN = 310;
    public static final int CREDITS_TICKS=840, RETURN_FADE_TICKS=52;
    public static float whiteout(float time) {
        return time<DETONATION?0:1-ease((time-DETONATION-10)/35F);
    }

    private FinaleTimeline() { }

    public static float ease(float value) {
        double x = Math.clamp(value, 0F, 1F);
        return (float)Math.clamp(x * x * x * (x * (x * 6 - 15) + 10), 0, 1);
    }
    public static float charge(float time) { return ease((time - 18) / 150F); }
    public static float beam(float time) {
        return ease((time - BEAM_START) / 10F) * (1 - ease((time - (BEAM_END - 18)) / 18F));
    }
    public static float implosion(float time) {
        return ease((time - IMPLOSION_START) / (DETONATION - IMPLOSION_START));
    }
    public static float shockwave(float time) { return ease((time - DETONATION) / 38F); }
    public static float sunScale(float time) {
        float charged = 1 + charge(time) * .28F;
        if (time < DETONATION) return charged * (1 - implosion(time) * .88F);
        return .1536F + shockwave(time) * 3.6464F;
    }
}
