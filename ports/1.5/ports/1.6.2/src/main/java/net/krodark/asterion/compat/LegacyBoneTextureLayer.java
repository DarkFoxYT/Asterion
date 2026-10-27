//? if <26.1 {
package net.krodark.asterion.compat;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.*;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import net.minecraft.resources.Identifier;
import java.util.function.BiConsumer;

/** Keep conditional bone overlays on GeckoLib versions without shouldRenderBone. */
public class LegacyBoneTextureLayer<T extends GeoAnimatable, O, R extends GeoRenderState>
        extends CustomBoneTextureGeoLayer<T, O, R> {
    public LegacyBoneTextureLayer(GeoRenderer<T, O, R> renderer, String bone, Identifier texture) {
        super(renderer, bone, texture);
    }
    public boolean shouldRenderBone(R state) { return true; }
    @Override
    public void addPerBoneRender(RenderPassInfo<R> pass, BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
        if (shouldRenderBone(pass.renderState())) super.addPerBoneRender(pass, consumer);
    }
}
//?}
