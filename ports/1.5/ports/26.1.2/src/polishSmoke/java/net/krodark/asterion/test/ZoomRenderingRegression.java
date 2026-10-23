package net.krodark.asterion.test;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderLayer;
import com.meekdev.amnetic.client.pipeline.internal.LayerRedirect;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;

public final class ZoomRenderingRegression {
    public static boolean hideHands;
    public static int interruptedLayers;
    private static int frames;
    private static float minLens = Float.POSITIVE_INFINITY, maxLens;

    public static void register() {
        if (!Boolean.getBoolean("asterion.zoomSmoke")) return;
        Pipeline.addLayer(RenderLayer.HAND, (context, layer) -> {});
        Pipeline.add(RenderStage.SETUP, Integer.MIN_VALUE, context -> {
            if (Minecraft.getInstance().level == null) return;
            if (LayerRedirect.active() != null) throw new AssertionError("Cancelled zoom hand left layer redirection active");
            hideHands = (frames / 8) % 2 == 0;
            float[] lenses = {70, 30, 12, 3, 12, 30, 70};
            AmneticCamera.setFov(lenses[(frames / 8) % lenses.length]);
        });
        Pipeline.add(RenderStage.BEFORE_GUI, Integer.MIN_VALUE, context -> {
            if (Minecraft.getInstance().level == null) return;
            var camera = CameraSnapshot.current();
            // The level is assigned during the loading screen before its first world render.
            if (camera == null && frames == 0) return;
            if (camera == null || !camera.projection.isFinite() || !camera.invViewProj.isFinite())
                throw new AssertionError("Zoom produced an invalid light reconstruction camera");
            minLens = Math.min(minLens, camera.projection.m11());
            maxLens = Math.max(maxLens, camera.projection.m11());
            if (++frames == 120) {
                hideHands = false;
                AmneticCamera.clearFov();
                if (interruptedLayers == 0 || maxLens / minLens < 4)
                    throw new AssertionError("Zoom or interrupted-hand scenario did not execute");
                Asterion.LOGGER.info("ASTERION_ZOOM_26 PASS: 120 finite zoom frames and {} interrupted layers recovered", interruptedLayers);
                Minecraft.getInstance().stop();
            }
        });
    }
}
