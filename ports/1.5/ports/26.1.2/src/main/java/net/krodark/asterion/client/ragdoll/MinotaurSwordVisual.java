package net.krodark.asterion.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;

 
public final class MinotaurSwordVisual {
    private static final java.util.Map<Long, MinotaurAxeVisual.Release> RELEASES = new java.util.HashMap<>();
    public static void captureHand(int owner, int side, PoseStack poses, CameraRenderState camera) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        if (RELEASES.size() > 128) RELEASES.clear();
        var matrix = poses.last().pose();
        var center = matrix.transformPosition(new org.joml.Vector3f(0,
                (float)net.krodark.asterion.entity.MinotaurAxeEntity.SWORD_CENTER_Y, 0));
        RELEASES.put(((long)owner << 1) | (side > 0 ? 1 : 0), new MinotaurAxeVisual.Release(
                camera.pos.add(center.x, center.y, center.z), matrix.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize(),
                level.getGameTime(), level));
    }
    public static MinotaurAxeVisual.Release release(int owner, int side) {
        var value = RELEASES.get(((long)owner << 1) | (side > 0 ? 1 : 0));
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return value != null && level == value.level() && level.getGameTime() - value.tick() < 8 ? value : null;
    }
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
