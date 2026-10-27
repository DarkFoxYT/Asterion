//? if <26.1 {
package net.krodark.asterion.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoObjectRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;

/** Preserve fractional animation time with GeckoLib 5.4's integer object-pass overload. */
public final class LegacyGeoObjectPass {
    private LegacyGeoObjectPass() { }
    public static <T extends GeoAnimatable, O, R extends GeoRenderState> void render(
            GeoObjectRenderer<T, O, R> renderer, T object, O related, PoseStack poses,
            SubmitNodeCollector tasks, CameraRenderState camera, int light, float partial) {
        R state = renderer.fillRenderState(object, related, renderer.createRenderState(object, related), partial);
        state.addGeckolibData(DataTickets.PACKED_LIGHT, light);
        renderer.performRenderPass(state, poses, tasks, camera);
    }
}
//?}
