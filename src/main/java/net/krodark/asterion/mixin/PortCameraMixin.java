package net.krodark.asterion.mixin;

import net.krodark.asterion.port.client.PortCrucibleCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
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
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;

    @Inject(method = "setup", at = @At("TAIL"))
    private void asterion$applyForgeCamera(BlockGetter level, Entity entity, boolean detached,
                                           boolean reverse, float partialTick, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || entity != client.player) return;
        if (client.player.getVehicle() instanceof net.krodark.asterion.entity.ScarletCentipedeEntity mount) {
            Vec3 normal = mount.passengerNormal(client.player, partialTick);
            Vec3 seat = mount.passengerPosition(client.player, partialTick);
            setPosition(seat.add(normal.scale(-client.player.getEyeHeight())));
            Quaternionf tilt = new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                    new Vector3f((float)-normal.x, (float)-normal.y, (float)-normal.z));
            rotation.premul(tilt);
            forwards.rotate(tilt);
            up.rotate(tilt);
            left.rotate(tilt);
        }
        Vec3 ragdollCamera = net.krodark.asterion.port.client.PortRagdolls.cameraPosition(getPosition(), partialTick);
        if (ragdollCamera != null) setPosition(ragdollCamera);
        net.krodark.asterion.port.client.PortCinematics.CameraPose cinematic =
                net.krodark.asterion.port.client.PortCinematics.cameraPose(getPosition(), partialTick);
        if (cinematic != null) {
            setPosition(cinematic.position());
            setRotation(cinematic.yaw(), cinematic.pitch());
            if (Math.abs(cinematic.roll()) > 0.001F) {
                Quaternionf roll = new Quaternionf().fromAxisAngleRad(forwards.x, forwards.y, forwards.z,
                        (float)Math.toRadians(cinematic.roll()));
                rotation.premul(roll);
                up.rotate(roll);
                left.rotate(roll);
            }
        }
        PortCrucibleCamera.CameraPose pose = PortCrucibleCamera.cameraPose(
                getPosition(), getYRot(), getXRot(), partialTick);
        if (pose != null) {
            setPosition(pose.position());
            setRotation(pose.yaw(), pose.pitch());
        }
        net.krodark.asterion.port.client.PortDeadSunEvents.Sample sample =
                net.krodark.asterion.port.client.PortDeadSunEvents.sample(getPosition(), partialTick);
        if (sample != net.krodark.asterion.port.client.PortDeadSunEvents.Sample.NONE) {
            setPosition(getPosition().add(sample.cameraOffset()));
            setRotation(getYRot() + sample.yaw(), getXRot() + sample.pitch());
        }
    }
}
