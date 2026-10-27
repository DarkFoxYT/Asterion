package net.krodark.asterion.client.cinematic;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

final class CinematicCameraCollision {
    private CinematicCameraCollision() { }
    static Vec3 clear(Vec3 focus,Vec3 camera) {
        var level=Minecraft.getInstance().level;
        if (level==null) return camera;
        return net.krodark.asterion.physics.CinematicMotion.clearCamera(focus,camera,.35,(from,to) -> {
            var hit=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty()));
            return hit.getType()==HitResult.Type.MISS?null:hit.getLocation();
        });
    }
}
