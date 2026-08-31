package net.krodark.asterion.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** Reuses the authored physics sword mesh for equipped and hip-mounted swords. */
public final class MinotaurSwordVisual {
    private static final DebrisPhysicsObject SWORD = new DebrisPhysicsObject(9);
    private static final DebrisGeoRenderer RENDERER = new DebrisGeoRenderer();
    private MinotaurSwordVisual() { }

    public static void submit(PoseStack poses, SubmitNodeCollector tasks, CameraRenderState camera, int light, boolean drawn) {
        poses.pushPose();
        // Grip the lower handle so the large crossguard clears the twelve-pixel fist bore.
        // The 92-pixel physics mesh is longer than the entire leg. Fit the stowed
        // copy between belt and ankle; retain the enlarged combat silhouette in hand.
        float scale = drawn ? 1F : .5F;
        poses.scale(scale, scale, scale);
        poses.translate(0, 6.0 / 16, 0);
        RENDERER.performRenderPass(SWORD, null, poses, tasks, camera, light, 0);
        poses.popPose();
    }
}
