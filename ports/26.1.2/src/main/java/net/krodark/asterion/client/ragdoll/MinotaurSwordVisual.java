package net.krodark.asterion.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;

 
public final class MinotaurSwordVisual {
    private static final DebrisPhysicsObject SWORD = new DebrisPhysicsObject(9);
    private static final DebrisGeoRenderer RENDERER = new DebrisGeoRenderer();
    private MinotaurSwordVisual() { }

    public static void submit(PoseStack poses, SubmitNodeCollector tasks,
                              CameraRenderState camera, int light) {
        poses.pushPose();
         
         
        poses.translate(0, 6.0 / 16, 0);
        RENDERER.performRenderPass(SWORD, null, poses, tasks, camera, light, 0);
        poses.popPose();
    }
}
