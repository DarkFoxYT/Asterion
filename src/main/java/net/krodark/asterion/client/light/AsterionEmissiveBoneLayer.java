package net.krodark.asterion.client.light;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import com.geckolib.util.RenderUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;

 
public class AsterionEmissiveBoneLayer<T extends GeoAnimatable, O, R extends GeoRenderState>
        extends CustomBoneTextureGeoLayer<T, O, R> {
    public AsterionEmissiveBoneLayer(GeoRenderer<T, O, R> renderer, String bone, Identifier texture) {
        super(renderer, bone, texture);
    }

     
    protected float emissiveStrength(R state) { return 1f; }
    protected float surfaceBrightness(R state) { return 0.8f; }
    protected boolean enhancedSurface(R state) { return false; }
    protected Identifier amneticEmissionMesh(R state) { return null; }
     
    protected boolean backfaceCulling(R state) { return true; }
    protected boolean usesModelTextureCoordinates() { return false; }
    protected int emissiveColor(R state) { return 0xFFFFFFFF; }

    @Override
    protected void renderBone(RenderPassInfo<R> pass, GeoBone bone, SubmitNodeCollector tasks) {
        R state = pass.renderState();
        if (!shouldRenderBone(state) || !(bone instanceof CuboidGeoBone cuboid)) return;
        Identifier texture = getTextureResource(state);
        Identifier base = this.renderer.getTextureLocation(state);
        float widthRatio = 1f, heightRatio = 1f;
         
        if (!texture.equals(base) && !usesModelTextureCoordinates()) {
            var size = RenderUtil.getTextureDimensions(texture);
            var baseSize = RenderUtil.getTextureDimensions(base);
            widthRatio = baseSize.firstInt() / (float) size.firstInt();
            heightRatio = baseSize.secondInt() / (float) size.secondInt();
        }
        int color = EmissiveBoneMesh.dimColor(emissiveColor(state), surfaceBrightness(state));
        if ((color >>> 24) == 0) return;
        var mesh = EmissiveBoneMesh.of(cuboid);
        float uScale = widthRatio, vScale = heightRatio;
        Identifier emissionMesh = amneticEmissionMesh(state);
         
        var stack = pass.poseStack();
        stack.pushPose();
        try {
            bone.translateAwayFromPivotPoint(stack);
            tasks.submitCustomGeometry(stack, AsterionEmissiveBuffer.renderType(texture, enhancedSurface(state)),
                    (pose, buffer) -> {
                        mesh.render(pose, buffer, color, uScale, vScale);
                        if (emissionMesh != null)
                            AmneticBoneEmission.submit(emissionMesh, mesh, texture, pose.pose(), color,
                                    uScale, vScale, emissiveStrength(state), backfaceCulling(state));
                    });
        } finally {
            stack.popPose();
        }
    }
}
