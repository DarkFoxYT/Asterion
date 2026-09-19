package net.krodark.asterion.entity;

import software.bernie.geckolib.animation.AnimationController;

/** GeckoLib 4 controller used by the Minecraft 1.21.1 build. */
public final class MinotaurAnimationController extends AnimationController<MinotaurEntity> {
    private double requestedTick = Double.NaN;

    public MinotaurAnimationController(MinotaurEntity boss, AnimationStateHandler<MinotaurEntity> handler) {
        super(boss, "movement", 0, handler);
    }

    public void entryBlend(boolean entry, MinotaurEntity.AnimationState pose) {
        transitionLength(entry && (pose == MinotaurEntity.AnimationState.LEAP
                || pose == MinotaurEntity.AnimationState.LAND) ? 2 : entry ? 6 : 4);
    }

    public void samplePose(double seconds, double age, boolean loop) {
        requestedTick = Math.max(0, seconds * 20.0D);
    }

    @Override
    protected double adjustTick(double tick) {
        // Keep GeckoLib's own clock while crossing between clips, then seek the
        // active clip from the authoritative attack timeline. This restores the
        // complete leap/land sequence without snapping pose boundaries.
        double adjusted = super.adjustTick(tick);
        return getAnimationState() == State.RUNNING && Double.isFinite(requestedTick)
                ? requestedTick : adjusted;
    }

    public static double sampleSeconds(double requested, double length, boolean loop) {
        if (length <= 0) return 0;
        return loop ? (requested % length + length) % length
                : net.krodark.asterion.port.compat.MathCompat.clamp(requested, 0, Math.max(0, length - .00001));
    }
}
