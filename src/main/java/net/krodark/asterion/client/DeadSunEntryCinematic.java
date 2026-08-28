package net.krodark.asterion.client;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A short, fully client-authored arrival shot. It never moves or teleports the player. */
public final class DeadSunEntryCinematic {
    private static final int HOLD_END = 72;
    private static final int END_TICKS = 158;
    private static boolean active;
    private static int ticks;
    private static float returnYaw;
    private static float returnPitch;
    private static CameraType previousCamera;

    private DeadSunEntryCinematic() { }

    public static void begin() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)
                || !AsterionConfig.INSTANCE.cinematicsEnabled) return;
        returnYaw = client.player.getYRot();
        returnPitch = client.player.getXRot();
        previousCamera = client.options.getCameraType();
        client.options.setCameraType(CameraType.FIRST_PERSON);
        ticks = 0;
        active = true;
    }

    public static void tick(Minecraft client) {
        if (!active) return;
        if (client.player == null || client.level == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            finish(client);
            return;
        }
        // Lock both key mappings and the underlying view angles. Raw mouse movement can still
        // arrive during the shot, but is discarded here instead of accumulating into a snap.
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keyShift.setDown(false);
        client.player.setYRot(returnYaw);
        client.player.setXRot(returnPitch);
        client.player.yHeadRot = returnYaw;
        client.player.yBodyRot = returnYaw;
        client.player.setDeltaMovement(0.0D, client.player.getDeltaMovement().y, 0.0D);
        if (client.options.getCameraType() != CameraType.FIRST_PERSON)
            client.options.setCameraType(CameraType.FIRST_PERSON);
        if (++ticks >= END_TICKS) finish(client);
    }

    private static void finish(Minecraft client) {
        active = false;
        ticks = 0;
        if (previousCamera != null) client.options.setCameraType(previousCamera);
        previousCamera = null;
    }

    public static boolean isActive() { return active; }

    public static CameraPose cameraPose(Vec3 basePosition, float partialTick) {
        if (!active) return null;
        float time = ticks + partialTick;
        // Begin over the maze instead of orbiting into the Dead Sun. The camera follows one
        // straight, eased rail back to the live player/ragdoll camera, which avoids the old
        // corkscrew-like rotation and keeps the roof-line readable throughout the shot.
        double yawRadians = returnYaw * Mth.DEG_TO_RAD;
        Vec3 backwards = new Vec3(Math.sin(yawRadians), 0.0D, -Math.cos(yawRadians));
        Vec3 horizontalShot = basePosition.add(backwards.scale(52.0D));
        Vec3 detachedPosition = new Vec3(horizontalShot.x,
                Math.max(110.0D, basePosition.y + 46.0D), horizontalShot.z);
        Vec3 mazeRoofFocus = new Vec3(basePosition.x, 76.0D, basePosition.z);
        Vec3 delta = mazeRoofFocus.subtract(detachedPosition);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float shotYaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float shotPitch = (float)-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        float shotWeight = time <= HOLD_END ? 1.0F
                : 1.0F - smoother((time - HOLD_END) / (END_TICKS - HOLD_END));
        // Do not rotate during the rail movement. Only position is keyframed; the normal player
        // view resumes after the cinematic fade, with no accumulated mouse input.
        Vec3 position = basePosition.lerp(detachedPosition, shotWeight);
        return new CameraPose(position, shotYaw, shotPitch);
    }

    public static float radianceStrength() {
        if (!active) return 0.0F;
        float time = ticks;
        if (time < 12.0F) return 0.0F;
        if (time < 26.0F) return smoother((time - 12.0F) / 14.0F);
        if (time < 42.0F) return 1.0F;
        return 1.0F - smoother((time - 42.0F) / 36.0F);
    }

    private static float smoother(float value) {
        float x = Mth.clamp(value, 0.0F, 1.0F);
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) { }
}
