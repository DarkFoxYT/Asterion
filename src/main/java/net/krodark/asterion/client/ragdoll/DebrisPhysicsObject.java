package net.krodark.asterion.client.ragdoll;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;

/** A render-only GeckoLib object owned by the client debris simulation. */
final class DebrisPhysicsObject implements GeoAnimatable {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final int variant;

    DebrisPhysicsObject(int variant) {
        this.variant = variant;
    }

    int variant() {
        return variant;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Debris motion is driven by the rigid-body simulation, not a keyframe animation.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
