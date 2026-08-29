package net.krodark.asterion.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import com.geckolib.renderer.GeoObjectRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;

final class DebrisGeoRenderer extends GeoObjectRenderer<DebrisPhysicsObject, Void, GeoRenderState> {
    DebrisGeoRenderer() {
        super(new DebrisGeoModel());
    }

    @Override
    public void addRenderData(DebrisPhysicsObject debris, Void relatedObject,
                              GeoRenderState renderState, float partialTick) {
        renderState.addGeckolibData(DebrisGeoModel.VARIANT, debris.variant());
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo) {
        // The physics system supplies an exact world-space origin and rotation.
    }
}
