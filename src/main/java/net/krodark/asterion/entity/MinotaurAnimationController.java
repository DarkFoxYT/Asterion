package net.krodark.asterion.entity;

import software.bernie.geckolib.animation.AnimationController;

/** GeckoLib 4 controller used by the Minecraft 1.21.1 build. */
public final class MinotaurAnimationController extends AnimationController<MinotaurEntity> {
    public MinotaurAnimationController(MinotaurEntity boss, AnimationStateHandler<MinotaurEntity> handler) {
        super(boss, "movement", 0, handler);
    }

    public void entryBlend(boolean entry, MinotaurEntity.AnimationState pose) {
        transitionLength(!entry ? 0 : pose == MinotaurEntity.AnimationState.LEAP
                || pose == MinotaurEntity.AnimationState.LAND ? 2 : 6);
    }

    public void samplePose(double seconds, double age, boolean loop) {
        // GeckoLib 4 has no public equivalent of GeckoLib 5's render-state timeline seeking.
    }

    public static double sampleSeconds(double requested, double length, boolean loop) {
        if (length <= 0) return 0;
        return loop ? (requested % length + length) % length
                : Math.clamp(requested, 0, Math.max(0, length - .00001));
    }
}
