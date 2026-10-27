package net.krodark.asterion.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;

/** The older render state does not carry the culling frustum or camera FOV. */
public final class FrameRenderAccess {
    private static Frustum frustum;
    private FrameRenderAccess() { }
    public static void recordFrustum(Frustum value) { frustum = value; }
    public static Frustum frustum() { return frustum; }
    public static boolean visible(AABB box) { return frustum == null || frustum.isVisible(box); }
    public static void clear() { frustum = null; }
    public static float fov(Camera camera) {
        //? if <26.1 {
        /*Minecraft client = Minecraft.getInstance();
        return ((net.krodark.asterion.mixin.GameRendererFovInvoker)client.gameRenderer).asterion$fov(
                camera, client.getDeltaTracker().getGameTimeDeltaPartialTick(true), true);
        *///?} else {
        return camera.getFov();
        //?}
    }
}
