package net.krodark.asterion.mixin;

import net.krodark.asterion.port.client.PortCrucibleCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class PortCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow public abstract Vec3 getPosition();
    @Shadow public abstract float getXRot();
    @Shadow public abstract float getYRot();

    @Inject(method = "setup", at = @At("TAIL"))
    private void asterion$applyForgeCamera(BlockGetter level, Entity entity, boolean detached,
                                           boolean reverse, float partialTick, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || entity != client.player) return;
        PortCrucibleCamera.CameraPose pose = PortCrucibleCamera.cameraPose(
                getPosition(), getYRot(), getXRot(), partialTick);
        if (pose == null) return;
        setPosition(pose.position());
        setRotation(pose.yaw(), pose.pitch());
    }
}
