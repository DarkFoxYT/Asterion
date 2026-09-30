package net.krodark.asterion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.krodark.asterion.update.underworld.client.LimboBoatRenderPose;
import net.krodark.asterion.update.underworld.world.LimboBoatPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractBoatRenderer;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoatRenderer.class)
public abstract class LimboBoatRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;Lnet/minecraft/client/renderer/entity/state/BoatRenderState;F)V", at = @At("TAIL"))
    private void asterion$wavePose(AbstractBoat boat, BoatRenderState state, float partial, CallbackInfo ci) {
        var pose = (LimboBoatPose)boat;
        ((LimboBoatRenderPose)state).limboTilt(pose.limboPitch(partial), pose.limboRoll(partial));
    }
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/BoatRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void asterion$tiltHull(BoatRenderState state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        var pose = (LimboBoatRenderPose)state;
        stack.mulPose(Axis.XP.rotationDegrees(pose.limboPitch()));
        stack.mulPose(Axis.ZP.rotationDegrees(pose.limboRoll()));
    }
}
