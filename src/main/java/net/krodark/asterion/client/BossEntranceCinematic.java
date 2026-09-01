package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.block.MinotaurDoorMotion;
import net.krodark.asterion.network.BossEntrancePayload;
import net.krodark.asterion.worldgen.MinotaurArenaEntrances;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class BossEntranceCinematic {
    private static boolean active, showShot, finished;
    private static int ticks, duration;
    private static Direction door;
    private static CameraType previousCamera;
    private static boolean previousCull;
    private static float returnYaw, returnPitch;
    private BossEntranceCinematic() { }

    public static void register() {
        HudElementRegistry.addLast(Asterion.id("boss_entrance"), (graphics, tracker) -> {
            if (!active || !showShot) return;
            float fade = Math.min(MinotaurDoorMotion.ease(ticks / 7F), MinotaurDoorMotion.ease((duration - ticks) / 14F));
            int height = Math.round(graphics.guiHeight() * .09F * fade);
            graphics.fill(0, 0, graphics.guiWidth(), height, 0xFF000000);
            graphics.fill(0, graphics.guiHeight() - height, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        });
    }

    public static void receive(BossEntrancePayload payload) {
        Minecraft client = Minecraft.getInstance();
        finish(client);
        if (payload.duration() <= 0 || client.player == null || client.level == null) return;
        door = payload.bossDoor();
        if (!door.getAxis().isHorizontal()) return;
        duration = Math.clamp(payload.duration(), 1, 200);
        finished = false;
        ticks = Math.clamp(payload.elapsed(), 0, duration);
        active = true;
        showShot = AsterionConfig.INSTANCE.cinematicsEnabled;
        returnYaw = client.player.getYRot(); returnPitch = client.player.getXRot();
        if (!showShot) return;
        previousCamera = client.options.getCameraType();
        previousCull = client.smartCull;
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.smartCull = false;
        CinematicHud.begin(client);
        client.levelRenderer.getSectionOcclusionGraph().invalidate();
    }

    public static boolean isActive() { return active; }
    public static boolean hasFinished() { return finished; }
    public static void tick(Minecraft client) {
        if (!active) return;
        if (client.player == null || client.level == null || !client.player.isAlive()
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL) || ++ticks >= duration + 10) {
            finish(client); return;
        }
        client.player.setDeltaMovement(Vec3.ZERO);
        client.player.setYRot(returnYaw); client.player.setXRot(returnPitch);
        if (showShot) {
            CinematicHud.maintain(client);
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.smartCull = false;
        }
    }

    public static CameraPose cameraPose(Vec3 playerEye, float partial) {
        if (!active || !showShot) return null;
        float time = ticks + partial;
        Vec3 inward = door.getOpposite().getUnitVec3();
        Vec3 doorway = Vec3.atBottomCenterOf(MinotaurArenaEntrances.door(door));
        float recoil = MinotaurDoorMotion.ease((time - 68) / 20F);
        Vec3 camera = doorway.add(inward.scale(14.0 + recoil * 4.0)).add(0, 2.4 + recoil * .65, 0)
                .add(door.getClockWise().getUnitVec3().scale(1.8 - recoil * 3.0));
        float impact = 0;
        for (int beat : new int[]{8, 26, 44, 70}) {
            float age = time - beat;
            if (age >= 0 && age < 10) impact += (beat == 70 ? .26F : .075F) * (1 - age / 10F);
        }
        camera = camera.add(Math.sin(time * 2.7) * impact, Math.cos(time * 3.4) * impact * .65, 0);
        Vec3 focus = doorway.add(inward.scale(1.5)).add(0, 3.7, 0);
        float returning = MinotaurDoorMotion.ease((time - (duration - 20)) / 20F);
        camera = camera.lerp(playerEye, returning);
        Vec3 delta = focus.subtract(camera);
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
        return new CameraPose(camera, Mth.rotLerp(returning, yaw, returnYaw), Mth.lerp(returning, pitch, returnPitch));
    }

    public static void finish(Minecraft client) {
        if (!active) return;
        active = false;
        finished = true;
        if (showShot) {
            if (previousCamera != null) client.options.setCameraType(previousCamera);
            client.smartCull = previousCull;
            CinematicHud.end(client);
            if (client.level != null) client.levelRenderer.getSectionOcclusionGraph().invalidate();
        }
        showShot = false; previousCamera = null;
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) { }
}
