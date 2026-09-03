package net.krodark.asterion.client;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.network.RoofCollapsePayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Short arena phase-change shot: roof fractures, falls, and buries the Minotaur. */
public final class RoofCollapseCinematic {
    private static boolean active, showShot;
    private static int ticks, duration;
    private static Vec3 center, openingEye;
    private static float returnYaw, returnPitch;
    private static CameraType previousCamera;
    private static boolean previousCull;
    private RoofCollapseCinematic() { }

    public static void begin(RoofCollapsePayload payload) {
        Minecraft client = Minecraft.getInstance();
        finish(client);
        if (client.player == null || client.level == null) return;
        center = payload.center();
        duration = Math.clamp(payload.duration(), 60, 180);
        ticks = 0;
        active = true;
        showShot = AsterionConfig.INSTANCE.cinematicsEnabled;
        openingEye = client.player.getEyePosition();
        returnYaw = client.player.getYRot(); returnPitch = client.player.getXRot();
        if (!showShot) return;
        previousCamera = client.options.getCameraType();
        previousCull = client.smartCull;
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.smartCull = false;
        CinematicHud.begin(client);
        client.levelRenderer.getSectionOcclusionGraph().invalidate();
    }

    public static void tick(Minecraft client) {
        if (!active) return;
        if (client.player == null || client.level == null || !client.player.isAlive()
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL) || ++ticks >= duration) {
            finish(client); return;
        }
        if (showShot) {
            CinematicHud.maintain(client);
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.smartCull = false;
        }
    }

    public static CameraPose cameraPose(Vec3 playerEye, float partial) {
        if (!active || !showShot || center == null) return null;
        float time = ticks + partial;
        float enter = smoother(time / 18F);
        float leave = smoother((time - (duration - 20F)) / 20F);
        Vec3 source = openingEye == null ? playerEye : openingEye;
        Vec3 outward = source.subtract(center).multiply(1, 0, 1);
        if (outward.lengthSqr() < 1.0E-5D) outward = new Vec3(0, 0, 1);
        outward = outward.normalize();
        Vec3 side = new Vec3(-outward.z, 0, outward.x);
        double orbit = Math.sin(time * .018D) * 4.5D;
        Vec3 shot = center.add(outward.scale(18.0D)).add(side.scale(orbit)).add(0, 10.5D, 0);
        Vec3 position = source.lerp(shot, enter).lerp(playerEye, leave);
        float fall = smoother((time - 28F) / 58F);
        Vec3 roof = center.add(0, 39.0D, 0);
        Vec3 crushedBoss = center.add(0, 2.2D, 0);
        Vec3 focus = roof.lerp(crushedBoss, fall);
        float pressure = smoother(time / 68F) * (1F - smoother((time - 108F) / 24F));
        float crush = 1F - Mth.clamp(Math.abs(time - 74F) / 15F, 0F, 1F);
        double force = pressure * .42D + smoother(crush) * 1.05D;
        double sx = (Math.sin(time * 2.83D) + Math.sin(time * .71D + 1.4D) * .65D) * force;
        double sy = (Math.sin(time * 3.47D + .8D) + Math.sin(time * .93D) * .55D) * force * .62D;
        position = position.add(sx, sy, -sx * .48D);
        Vec3 delta = focus.subtract(position);
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z)) + (float)(sx * 2.6D);
        float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()))
                + (float)(sy * 2.2D);
        return new CameraPose(position, Mth.rotLerp(leave, yaw, returnYaw),
                Mth.lerp(leave, pitch, returnPitch));
    }

    public static boolean isActive() { return active; }
    public static void finish(Minecraft client) {
        if (!active) return;
        active = false;
        if (showShot) {
            if (previousCamera != null) client.options.setCameraType(previousCamera);
            client.smartCull = previousCull;
            CinematicHud.end(client);
            if (client.level != null) client.levelRenderer.getSectionOcclusionGraph().invalidate();
        }
        showShot = false; center = null; openingEye = null; previousCamera = null;
    }
    private static float smoother(float value) {
        float x = Mth.clamp(value, 0F, 1F);
        return x * x * x * (x * (x * 6F - 15F) + 10F);
    }
    public record CameraPose(Vec3 position, float yaw, float pitch) { }
}
