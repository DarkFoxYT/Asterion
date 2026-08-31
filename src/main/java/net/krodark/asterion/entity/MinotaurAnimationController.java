package net.krodark.asterion.entity;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

/** Keep action poses out of GeckoLib's trailing reset stage and blend directly between moving poses. */
public final class MinotaurAnimationController extends AnimationController<MinotaurEntity> {
    private double requestedSeconds = -1, poseAge;
    private AnimationPoint blendFrom;
    // Blend evaluated poses in the renderer: raw clip snapshots cannot preserve an
    // interrupted crossfade and used to snap back to the outgoing clip's unblended pose.
    public MinotaurAnimationController(AnimationStateHandler<MinotaurEntity> handler) { super("movement", 0, handler); }

    @Override public void setAnimation(RawAnimation animation) {
        boolean changed = !animation.equals(currentRawAnimation);
        AnimationPoint outgoing = animationPoint;
        super.setAnimation(animation);
        if (changed) {
            // createNext shares mutable keyframe indices. A crossfade needs an independent snapshot.
            blendFrom = outgoing == null ? null : AnimationPoint.createFor(outgoing.animation(),
                    outgoing.easingOverride(), outgoing.loopType(), outgoing.animTime());
            transitionFromPoint = blendFrom;
        }
    }

    public void samplePose(double seconds, double age) { requestedSeconds = seconds; poseAge = Math.max(0, age); }

    @Override protected void progressExistingAnimation(MinotaurEntity boss, GeoRenderState state,
            double previousTime, double delta) {
        // Scripted clips have one clock. Advancing GeckoLib as well rewound the sampled
        // keyframe indices during the crossfade and briefly entered the trailing reset.
        if (requestedSeconds < 0) super.progressExistingAnimation(boss, state, previousTime, delta);
    }

    @Override protected boolean checkControllerState(MinotaurEntity boss, GeoRenderState state,
            AnimatableManager<MinotaurEntity> manager, GeoModel<MinotaurEntity> model) {
        boolean active = super.checkControllerState(boss, state, manager, model);
        if (poseAge < transitionTicks && blendFrom != null) transitionFromPoint = blendFrom;
        else { blendFrom = null; transitionFromPoint = null; }
        if (requestedSeconds < 0 || timeline == null || animationPoint == null) return active;
        // Exactly length() addresses the reset transition. Hold just inside the last authored frame instead.
        double seconds = Math.clamp(requestedSeconds, 0, Math.max(0, animationPoint.animation().length() - .00001));
        // Render states may be consumed later: never mutate a previous frame's keyframe cursor.
        animationPoint = AnimationPoint.createFor(animationPoint.animation(), animationPoint.easingOverride(),
                animationPoint.loopType(), seconds);
        timelineTime = poseAge < transitionTicks ? poseAge / 20.0 : transitionTicks / 20.0 + seconds;
        return true;
    }
}
