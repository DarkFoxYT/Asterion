package net.krodark.asterion.mixin;

import net.krodark.asterion.client.event.DeadSunClientEvents;
import net.krodark.asterion.client.DeadSunEntryCinematic;
import net.krodark.asterion.client.BossFinaleOverlay;
import net.krodark.asterion.client.BossEntranceCinematic;
import net.krodark.asterion.client.CursedBrazierCinematic;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Matrix4f;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique private Vec3 asterion$smoothedRagdollCamera;
    @Unique private float asterion$flamethrowerFovStrength;
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow public abstract Vec3 position();
    @Shadow public abstract float xRot();
    @Shadow public abstract float yRot();

    @Inject(method = "update", at = @At("HEAD"))
    private void asterion$lockRagdollPerspective(DeltaTracker tracker, CallbackInfo ci) {
        net.krodark.asterion.client.ragdoll.RagdollClientController
                .enforceRagdollCamera(Minecraft.getInstance());
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void asterion$flamethrowerFovPulse(CallbackInfoReturnable<Float> result) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean spraying = minecraft.player != null && minecraft.player.isUsingItem()
                && minecraft.player.getUseItem().is(net.krodark.asterion.game.GameplayContent.FLAMETHROWER);
        asterion$flamethrowerFovStrength = Mth.lerp(.12F, asterion$flamethrowerFovStrength,
                spraying ? 1.0F : 0.0F);
        if (asterion$flamethrowerFovStrength < .002F) return;
        float seconds = (System.nanoTime() & 0x3fffffffffffffffL) * 1.0e-9F;
        float pulse = 1.15F + Mth.sin(seconds * 5.4F) * .7F;
        result.setReturnValue(result.getReturnValueF() + pulse * asterion$flamethrowerFovStrength);
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void asterion$followRagdollHead(DeltaTracker tracker, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        float partial = tracker.getGameTimeDeltaPartialTick(true);
        if (minecraft.player != null) {
            Vec3 handFeet = net.krodark.asterion.client.render.entity.MinotaurHandAttachment.feet(minecraft.player);
            if (handFeet != null) setPosition(position().add(handFeet.subtract(minecraft.player.getPosition(partial))));
        }
        if (minecraft.player == null) asterion$smoothedRagdollCamera = null;
        else {
            Vec3 head = DismembermentEngine.INSTANCE.playerTumbleCameraPosition(minecraft.player.getId(), partial);
            Vec3 torso = DismembermentEngine.INSTANCE.tumbleCameraAnchor(minecraft.player.getId(), partial);
            if (head == null && torso == null) asterion$smoothedRagdollCamera = null;
            else {
                // The head is a fast, light rigid body and made the view whip on every impact.
                // Follow the torso, lifted toward the head, while vanilla still owns yaw and pitch.
                Vec3 anchor = torso != null ? torso : head;
                Vec3 visualEye = torso != null && head != null ? torso.lerp(head, 0.38).add(0, 0.18, 0)
                        : anchor.add(0, 0.35, 0);
                Vec3 desired = visualEye.add(position().subtract(minecraft.player.getEyePosition(partial)));
                if (asterion$smoothedRagdollCamera == null || asterion$smoothedRagdollCamera.distanceToSqr(desired) > 6.25)
                    asterion$smoothedRagdollCamera = desired;
                else {
                    double blend = 1.0 - Math.pow(.48, Math.max(.25, tracker.getGameTimeDeltaTicks()));
                    asterion$smoothedRagdollCamera = asterion$smoothedRagdollCamera.lerp(desired, blend);
                }
                Vec3 safe = asterion$clipCamera(minecraft, anchor, asterion$smoothedRagdollCamera);
                asterion$smoothedRagdollCamera = safe;
                setPosition(DismembermentEngine.INSTANCE.pushCameraOutsideRagdoll(minecraft.player.getId(), safe));
            }
        }
        DeadSunEntryCinematic.CameraPose shot = DeadSunEntryCinematic.cameraPose(position(), partial);
        if (shot != null) {
            setPosition(shot.position());
            setRotation(shot.yaw(), shot.pitch());
        }
        BossFinaleOverlay.CameraPose finale = BossFinaleOverlay.cameraPose(position(), partial);
        if (finale != null) {
            setPosition(finale.position());
            setRotation(finale.yaw(), finale.pitch());
        }
        BossEntranceCinematic.CameraPose entrance = BossEntranceCinematic.cameraPose(position(), partial);
        if (entrance != null) {
            setPosition(entrance.position());
            setRotation(entrance.yaw(), entrance.pitch());
        }
        CursedBrazierCinematic.CameraPose brazier = CursedBrazierCinematic.cameraPose(position(), partial);
        if (brazier != null) {
            setPosition(brazier.position());
            setRotation(brazier.yaw(), brazier.pitch());
        }
        net.krodark.asterion.client.RoofCollapseCinematic.CameraPose collapse =
                net.krodark.asterion.client.RoofCollapseCinematic.cameraPose(position(), partial);
        if (collapse != null) {
            setPosition(collapse.position());
            setRotation(collapse.yaw(), collapse.pitch());
        }
        DeadSunClientEvents.Sample doorShake = net.krodark.asterion.client.MinotaurDoorShake.sample(position(), partial);
        if (doorShake != DeadSunClientEvents.Sample.NONE) {
            setPosition(position().add(doorShake.cameraOffset()));
            setRotation(yRot() + doorShake.yawDegrees(), xRot() + doorShake.pitchDegrees());
        }
        DeadSunClientEvents.Sample sample = DeadSunClientEvents.sample(partial);
        if (sample != DeadSunClientEvents.Sample.NONE) {
            setPosition(position().add(sample.cameraOffset()));
            setRotation(yRot() + sample.yawDegrees(), xRot() + sample.pitchDegrees());
        }
        if (shot != null || finale != null || entrance != null || brazier != null || collapse != null)
            asterion$rebuildCinematicFrustum(minecraft);
    }

    /** Camera.update creates its culling frustum before this tail injection moves the camera.
     * Rebuild it from the actual cutscene pose so terrain visibility follows the shot, not the
     * frozen player body's facing direction. The slightly wider culling FOV avoids edge pop-in
     * during fast orbit shots without rendering beyond the configured cinematic distance. */
    @Unique private void asterion$rebuildCinematicFrustum(Minecraft minecraft) {
        int width = Math.max(1, minecraft.getWindow().getWidth());
        int height = Math.max(1, minecraft.getWindow().getHeight());
        float cullingFov = Math.max(110.0F, minecraft.options.fov().get().floatValue());
        float farPlane = Math.max(256.0F, minecraft.options.getEffectiveRenderDistance() * 64.0F);
        Matrix4f view = ((Camera)(Object)this).getViewRotationMatrix(new Matrix4f());
        Matrix4f projection = new Matrix4f().perspective(cullingFov * Mth.DEG_TO_RAD,
                width / (float)height, 0.05F, farPlane);
        Frustum corrected = new Frustum(view, projection);
        Vec3 cameraPosition = position();
        corrected.prepare(cameraPosition.x, cameraPosition.y, cameraPosition.z);
        ((Camera)(Object)this).getCullFrustum().set(corrected);
    }

    @Unique private static Vec3 asterion$clipCamera(Minecraft minecraft, Vec3 anchor, Vec3 desired) {
        if (minecraft.level == null || minecraft.player == null) return desired;
        Vec3 travel = desired.subtract(anchor);
        double distance = travel.length(), permitted = distance;
        if (distance > 1.0e-6) for (int corner = 0; corner < 8; corner++) {
            double skin = .09;
            Vec3 offset = new Vec3((corner & 1) == 0 ? -skin : skin,
                    (corner & 2) == 0 ? -skin : skin, (corner & 4) == 0 ? -skin : skin);
            Vec3 from = anchor.add(offset);
            var hit = minecraft.level.clip(new ClipContext(from, desired.add(offset),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, minecraft.player));
            if (hit.getType() != HitResult.Type.MISS)
                permitted = Math.min(permitted, Math.max(0, from.distanceTo(hit.getLocation()) - .11));
        }
        Vec3 clipped = distance <= 1.0e-6 ? desired : anchor.add(travel.scale(permitted / distance));
        for (int step = 0; step <= 12; step++) {
            Vec3 candidate = clipped.lerp(anchor, step / 12.0);
            AABB volume = new AABB(candidate.x-.085,candidate.y-.085,candidate.z-.085,
                    candidate.x+.085,candidate.y+.085,candidate.z+.085);
            if (minecraft.level.noCollision(minecraft.player, volume)) return candidate;
        }
        return anchor;
    }
}
