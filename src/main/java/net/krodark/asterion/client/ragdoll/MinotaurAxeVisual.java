package net.krodark.asterion.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** The same authored physics mesh is used at the hand, on the back and in flight. */
public final class MinotaurAxeVisual {
    public record Release(net.minecraft.world.phys.Vec3 center, org.joml.Quaternionf rotation, long tick, Object level) { }
    private static final java.util.Map<Integer, Release> RELEASES = new java.util.HashMap<>();
    private static final DebrisPhysicsObject AXE = new DebrisPhysicsObject(8);
    private static final DebrisGeoRenderer RENDERER = new DebrisGeoRenderer();
    private MinotaurAxeVisual() { }
    public static void captureHand(int owner, PoseStack poses, CameraRenderState camera) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        if (RELEASES.size() > 64) RELEASES.clear();
        var matrix = poses.last().pose();
        var center = matrix.transformPosition(new org.joml.Vector3f(0, (float)net.krodark.asterion.entity.MinotaurAxeEntity.CENTER_Y, 0));
        RELEASES.put(owner, new Release(camera.pos.add(center.x, center.y, center.z),
                matrix.getUnnormalizedRotation(new org.joml.Quaternionf()), level.getGameTime(), level));
    }
    public static Release release(int owner) {
        var value = RELEASES.get(owner);
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return value != null && value.level == level && level != null && level.getGameTime() - value.tick < 8 ? value : null;
    }
    public static void submit(PoseStack poses, SubmitNodeCollector tasks, CameraRenderState camera, int light, float partial) {
        RENDERER.performRenderPass(AXE, null, poses, tasks, camera, light, partial);
    }
}
