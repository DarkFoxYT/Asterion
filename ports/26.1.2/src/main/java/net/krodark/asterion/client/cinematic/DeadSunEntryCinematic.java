package net.krodark.asterion.client.cinematic;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DeadSunEntryCinematic {
    private static final int END_TICKS = 260;
    private static final int REQUIRED_CHUNK_RADIUS = 6;
    private static Vec3 openingPosition;
    private static boolean active;
    private static int ticks;
    private static boolean externalShot;
    private static float returnYaw;
    private static float returnPitch;
    private static CameraType previousCamera;
    private static Boolean previousSmartCull;

    private DeadSunEntryCinematic() { }

    public static int requiredChunkRadius() {
        // Never wait for terrain outside the player or server's view distance.
        return AsterionConfig.INSTANCE.cinematicsEnabled
                ? Math.min(REQUIRED_CHUNK_RADIUS,
                        Math.max(0, Minecraft.getInstance().options.getEffectiveRenderDistance() - 1)) : 0;
    }

    public static void begin() {
        Minecraft client = Minecraft.getInstance();
        if (net.krodark.asterion.client.AsterionClient.isPlayback(client)) return;
        if (client.player == null || client.level == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)
                || !AsterionConfig.INSTANCE.cinematicsEnabled) return;
        returnYaw = client.player.getYRot();
        returnPitch = client.player.getXRot();
        previousCamera = client.options.getCameraType();
        if (previousSmartCull == null) previousSmartCull = client.smartCull;
        client.smartCull = false;
        client.options.setCameraType(CameraType.FIRST_PERSON);
        CinematicHud.begin(client);
        client.levelRenderer.getSectionOcclusionGraph().invalidate();
        openingPosition = client.player.getEyePosition();
        ticks = 0;
        active = true;
    }

    public static void tick(Minecraft client) {
        if (net.krodark.asterion.client.AsterionClient.isPlayback(client)) {
            if (isActive()) finish(client);
            return;
        }
        if (!active) return;
        if (client.player == null || client.level == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            finish(client);
            return;
        }
        CinematicHud.maintain(client);
        client.smartCull = false;
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
        if (++ticks == 90) client.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.WARDEN_ROAR, .65F, 1.4F));
        if (ticks >= END_TICKS) finish(client);
    }

    public static void finish(Minecraft client) {
        active = false;
        ticks = 0;
        if (previousCamera != null) client.options.setCameraType(previousCamera);
        previousCamera = null;
        if (previousSmartCull != null) client.smartCull = previousSmartCull;
        previousSmartCull = null;
        if (client.level != null) client.levelRenderer.getSectionOcclusionGraph().invalidate();
        CinematicHud.end(client);
    }

    public static boolean isActive() { return active; }
    public static boolean showsPlayer() { return active && externalShot; }

    public static CameraPose cameraPose(Vec3 basePosition, float partialTick) {
        if (!active) return null;
        float time = ticks + partialTick;
        float linear = Mth.clamp(time / END_TICKS, 0.0F, 1.0F);
        float progress = smoother(linear);
        AsterionConfig config = AsterionConfig.INSTANCE;
        Vec3 sun = new Vec3(config.deadSunX, config.deadSunHeight, config.deadSunZ);
        Vec3 anchor = openingPosition == null ? basePosition : openingPosition;
        Vec3 towardSun = sun.subtract(anchor);
        double heading = Mth.atan2(towardSun.z, towardSun.x);
        double angle = heading + Mth.lerp(progress, 3.05D, 3.23D);
        double radius = Mth.lerp(progress, 58.0D, 50.0D);
        double height = Math.max(200.0D,
                net.krodark.asterion.worldgen.LabyrinthLevels.MAZE_FLOOR_Y + config.wallHeight + 42.0D)
                + Math.sin(progress * Math.PI) * 8.0D;
        Vec3 railPosition = new Vec3(anchor.x + Math.cos(angle) * radius,
                height, anchor.z + Math.sin(angle) * radius);
         
         
        float dive = smoother((linear - .43F) / .57F);
        double lateral = 1 - Math.pow(1 - dive, 4);
        double downward = Math.pow(dive, 3);
        Vec3 position = railPosition.lerp(new Vec3(basePosition.x, height, basePosition.z), lateral);
        position = new Vec3(position.x, Mth.lerp(downward, height, basePosition.y), position.z);
         
        float sunReveal = smoother((linear - .10F) / .18F);
        float playerFocus = smoother((linear - .43F) / .21F);
        Vec3 focus = basePosition.add(0, -.7, 0).lerp(
                sun.add(0, -config.deadSunSize * .16, 0), (.65F + .35F * sunReveal) * (1 - playerFocus));
        Vec3 delta = focus.subtract(position);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
         
        float approachYaw = (float)(heading * Mth.RAD_TO_DEG) - 90.0F;
        float lookYaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float shotYaw = Mth.rotLerp(smoother((float)horizontal / 8.0F), approachYaw, lookYaw);
        float shotPitch = (float)-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        float viewReturn = smoother(Mth.clamp((linear - 0.68F) / 0.32F, 0.0F, 1.0F));
        shotYaw = Mth.rotLerp(viewReturn, shotYaw, returnYaw);
        shotPitch = Mth.lerp(viewReturn, shotPitch, returnPitch);
         
        double wind = Math.sin(Math.PI * smoother((linear - .60F) / .40F));
        double gust = wind * (.010 * Math.sin(time * .09) + .005 * Math.sin(time * .17));
        if (position.y > net.krodark.asterion.worldgen.LabyrinthLevels.MAZE_FLOOR_Y + config.wallHeight + 3)
            position = position.add(gust, gust * .3, -gust * .6);
        externalShot = position.distanceToSqr(basePosition) > 4;
        return new CameraPose(position, shotYaw, shotPitch);
    }

    public static float radianceStrength() {
        if (!active) return 0.0F;
        float time = ticks * (190F / END_TICKS);
        if (time < 18.0F) return 0.0F;
        if (time < 42.0F) return smoother((time - 18.0F) / 24.0F);
        if (time < 128.0F) return 1.0F;
        return 1.0F - smoother((time - 128.0F) / 48.0F);
    }

    private static float smoother(float value) {
        float x = Mth.clamp(value, 0.0F, 1.0F);
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) { }
}
