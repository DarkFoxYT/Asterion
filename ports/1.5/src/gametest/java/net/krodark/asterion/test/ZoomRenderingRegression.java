package net.krodark.asterion.test;

import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderLayer;
import com.meekdev.amnetic.client.pipeline.internal.LayerRedirect;
import com.meekdev.amnetic.client.render.GlState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Real client coverage: repeated FOV changes, hidden hands and raw shader cache recovery. */
public final class ZoomRenderingRegression {
    public static boolean hideHands;
    public static double magnification = 1;
    public static int interruptedLayers;
    private static int frames;
    private static boolean cacheChecked;
    private static net.minecraft.client.KeyMapping zoomKey;
    private static Class<?> zoomHandler;
    private static boolean realZoomObserved;

    public static void register() {
        if (!Boolean.getBoolean("asterion.zoomSmoke")) return;
        try {
            zoomHandler = Class.forName("de.keksuccino.justzoom.ZoomHandler");
            zoomKey = (net.minecraft.client.KeyMapping) Class.forName("de.keksuccino.justzoom.KeyMappings")
                    .getField("KEY_TOGGLE_ZOOM").get(null);
        } catch (ClassNotFoundException absent) { }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        Pipeline.addLayer(RenderLayer.HAND, (context, layer) -> {});
        WorldRenderEvents.START.register(context -> {
            if (LayerRedirect.active() != null) throw new AssertionError("Cancelled zoom hand left layer redirection active");
            hideHands = (frames / 8) % 2 == 0;
            double[] zoom = {1, 2, 5, 20, 5, 2, 1};
            magnification = zoom[(frames / 8) % zoom.length];
            if (zoomKey != null) {
                zoomKey.setDown(hideHands);
                try {
                    zoomHandler.getMethod("onInputTick").invoke(null);
                    realZoomObserved |= (Boolean) zoomHandler.getMethod("isZooming").invoke(null);
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            }
        });
        WorldRenderEvents.END.register(context -> {
            var client = Minecraft.getInstance();
            if (!cacheChecked) {
                var shader = net.minecraft.client.renderer.GameRenderer.getPositionShader();
                RenderSystem.setShader(() -> shader);
                shader.apply();
                GlState.beginFullscreen();
                GlState.endFullscreen();
                shader.apply();
                if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != shader.getId())
                    throw new AssertionError("Vanilla shader failed to rebind after Amnetic without hand rendering");
                shader.clear();
                cacheChecked = true;
            }
            if (++frames == 120) {
                magnification = 1;
                hideHands = false;
                if (zoomKey != null) {
                    zoomKey.setDown(false);
                    if (!realZoomObserved) throw new AssertionError("Just Zoom never activated");
                }
                if (interruptedLayers == 0) throw new AssertionError("Hand cancellation did not interrupt an active layer");
                ClientSmokeTest.verifyGraphicsAndTaa();
                Asterion.LOGGER.info("ASTERION_ZOOM PASS: 120 FOV/hidden-hand frames, {} interrupted layers recovered, vanilla shader rebind, Just Zoom active={}", interruptedLayers, realZoomObserved);
                client.stop();
            }
        });
    }
}
