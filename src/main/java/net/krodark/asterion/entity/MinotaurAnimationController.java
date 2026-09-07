package net.krodark.asterion.entity;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

 
public final class MinotaurAnimationController extends AnimationController<MinotaurEntity> {
    private double requestedSeconds = -1, poseAge;
    private AnimationPoint blendFrom;
    private boolean loopSample;
     
     
    public MinotaurAnimationController(AnimationStateHandler<MinotaurEntity> handler) { super("movement", 0, handler); }

    @Override public void setAnimation(RawAnimation animation) {
        boolean changed = !animation.equals(currentRawAnimation);
        AnimationPoint outgoing = animationPoint;
        super.setAnimation(animation);
        if (changed) {
             
            blendFrom = outgoing == null ? null : AnimationPoint.createFor(outgoing.animation(),
                    outgoing.easingOverride(), outgoing.loopType(), outgoing.animTime());
            transitionFromPoint = blendFrom;
        }
    }

    public void entryBlend(boolean entry, MinotaurEntity.AnimationState pose) {
        transitionTicks = !entry ? 0 : pose == MinotaurEntity.AnimationState.LEAP
                || pose == MinotaurEntity.AnimationState.LAND ? 2 : 6;
    }

    public void samplePose(double seconds, double age, boolean loop) {
        requestedSeconds = seconds;
        poseAge = Math.max(0, age);
        loopSample = loop;
    }

    public static double sampleSeconds(double requested, double length, boolean loop) {
        if (length <= 0) return 0;
        return loop ? (requested % length + length) % length
                : Math.clamp(requested, 0, Math.max(0, length - .00001));
    }

    @Override protected void progressExistingAnimation(MinotaurEntity boss, GeoRenderState state,
            double previousTime, double delta) {
         
         
        if (requestedSeconds < 0) super.progressExistingAnimation(boss, state, previousTime, delta);
    }

    @Override protected boolean checkControllerState(MinotaurEntity boss, GeoRenderState state,
            AnimatableManager<MinotaurEntity> manager, GeoModel<MinotaurEntity> model) {
        boolean active = super.checkControllerState(boss, state, manager, model);
        if (poseAge < transitionTicks && blendFrom != null) transitionFromPoint = blendFrom;
        else { blendFrom = null; transitionFromPoint = null; }
        if (requestedSeconds < 0 || timeline == null || animationPoint == null) return active;
         
        double seconds = sampleSeconds(requestedSeconds, animationPoint.animation().length(), loopSample);
         
        animationPoint = AnimationPoint.createFor(animationPoint.animation(), animationPoint.easingOverride(),
                animationPoint.loopType(), seconds);
        timelineTime = poseAge < transitionTicks ? poseAge / 20.0 : transitionTicks / 20.0 + seconds;
        return true;
    }
}
