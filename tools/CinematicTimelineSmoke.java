import net.krodark.asterion.game.FinaleTimeline;

/** Verify the finale can be sampled at arbitrary render rates without early strikes or scale jumps. */
public final class CinematicTimelineSmoke {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        require(FinaleTimeline.BEAM_START < FinaleTimeline.BEAM_END, "Empty beam window");
        require(FinaleTimeline.BEAM_END <= FinaleTimeline.IMPLOSION_START, "Beam overlaps core collapse");
        require(FinaleTimeline.DETONATION < FinaleTimeline.BLACKOUT_END
                && FinaleTimeline.BLACKOUT_END < FinaleTimeline.RETURN, "Return exposed before blackout");
        require(FinaleTimeline.whiteout(FinaleTimeline.DETONATION)==1,"Explosion never fills screen with white");
        require(FinaleTimeline.whiteout(230)==1 && FinaleTimeline.whiteout(265)==0,"White-to-black fade is incomplete");
        require(FinaleTimeline.CREDITS_TICKS>=600 && FinaleTimeline.RETURN_FADE_TICKS>0,"Credits have no readable window");
        float lastScale = FinaleTimeline.sunScale(-20);
        float lastWave = 0;
        for (int frame = -200; frame <= 3100; frame++) {
            float time = frame / 10F;
            float charge = FinaleTimeline.charge(time), beam = FinaleTimeline.beam(time);
            float wave = FinaleTimeline.shockwave(time), scale = FinaleTimeline.sunScale(time);
            require(Float.isFinite(scale) && scale >= .14F && scale <= 3.81F, "Invalid core size at " + time);
            require(charge >= 0 && charge <= 1 && beam >= 0 && beam <= 1, "Invalid energy at " + time);
            require(wave >= lastWave, "Shockwave reverses at " + time);
            require(Math.abs(scale - lastScale) < .03F, "Visible core jump at " + time);
            if (time < FinaleTimeline.BEAM_START || time >= FinaleTimeline.BEAM_END)
                require(beam == 0, "Discharge outside its server impact window");
            if (time > FinaleTimeline.IMPLOSION_START && time < FinaleTimeline.DETONATION)
                require(scale <= lastScale + .0001F, "Core grows during implosion");
            if (time > FinaleTimeline.DETONATION)
                require(scale >= lastScale - .0001F, "Core contracts after detonation");
            lastScale = scale; lastWave = wave;
        }
        require(FinaleTimeline.beam(120) > .99F, "No sustained solar discharge");
        require(FinaleTimeline.sunScale(FinaleTimeline.DETONATION) < .2F, "Core never contracts");
        require(FinaleTimeline.shockwave(FinaleTimeline.BLACKOUT_END) == 1, "Incomplete shockwave under blackout");
        System.out.println("Cinematic timeline passed: 3,301 sub-tick samples, impact windows, contraction, shockwave and blackout ordering.");
    }
}
