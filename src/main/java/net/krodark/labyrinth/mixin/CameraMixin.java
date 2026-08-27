package net.krodark.labyrinth.mixin;

import net.krodark.labyrinth.client.event.DeadSunClientEvents;
import net.krodark.labyrinth.client.ragdoll.DismembermentEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
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

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique private Vec3 labyrinth$smoothedRagdollCamera;
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow public abstract Vec3 position();
    @Shadow public abstract float xRot();
    @Shadow public abstract float yRot();

    @Inject(method = "update", at = @At("TAIL"))
    private void labyrinth$applyDeadSunRumble(DeltaTracker tracker, CallbackInfo callbackInfo) {
        DeadSunClientEvents.Sample sample = DeadSunClientEvents.sample(
                tracker.getGameTimeDeltaPartialTick(false));
        if (sample == DeadSunClientEvents.Sample.NONE) return;
        setPosition(position().add(sample.cameraOffset()));
        setRotation(yRot() + sample.yawDegrees(), xRot() + sample.pitchDegrees());
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void labyrinth$followRagdollHead(DeltaTracker tracker, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) { labyrinth$smoothedRagdollCamera = null; return; }
        float partial = tracker.getGameTimeDeltaPartialTick(true);
        Vec3 head = DismembermentEngine.INSTANCE.playerTumbleCameraPosition(minecraft.player.getId(), partial);
        if (head == null) { labyrinth$smoothedRagdollCamera = null; return; }
        Vec3 desired = head.add(position().subtract(minecraft.player.getEyePosition(partial)));
        Vec3 torso = DismembermentEngine.INSTANCE.playerTumbleCameraAnchorPosition(minecraft.player.getId(), partial);
        Vec3 anchor = minecraft.options.getCameraType().isFirstPerson() && torso != null ? torso : head;
        if (labyrinth$smoothedRagdollCamera == null || labyrinth$smoothedRagdollCamera.distanceToSqr(desired) > 6.25)
            labyrinth$smoothedRagdollCamera = desired;
        else {
            double blend = 1.0 - Math.pow(.72, Math.max(.25, tracker.getGameTimeDeltaTicks()));
            labyrinth$smoothedRagdollCamera = labyrinth$smoothedRagdollCamera.lerp(desired, blend);
        }
        Vec3 safe = labyrinth$clipCamera(minecraft, anchor, labyrinth$smoothedRagdollCamera);
        labyrinth$smoothedRagdollCamera = safe;
        setPosition(DismembermentEngine.INSTANCE.pushCameraOutsideRagdoll(minecraft.player.getId(), safe));
    }

    @Unique private static Vec3 labyrinth$clipCamera(Minecraft minecraft, Vec3 anchor, Vec3 desired) {
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
