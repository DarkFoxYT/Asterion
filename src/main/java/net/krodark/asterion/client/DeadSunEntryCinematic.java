package net.krodark.asterion.client;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DeadSunEntryCinematic {
    private static final int END_TICKS = 260;
    private static final int CINEMATIC_RENDER_DISTANCE = 12;
    private static final int REQUIRED_CHUNK_RADIUS = 6;
    private static Vec3 openingPosition;
    private static boolean active;
    private static int ticks;
    private static boolean externalShot;
    private static float returnYaw;
    private static float returnPitch;
    private static CameraType previousCamera;
    private static Integer previousRenderDistance;
    private static Boolean previousSmartCull;

    private DeadSunEntryCinematic() { }

    public static void prepareForArrival(Minecraft client) {
        if (!AsterionConfig.INSTANCE.cinematicsEnabled) return;
        int current = client.options.renderDistance().get();
        if (previousRenderDistance == null) previousRenderDistance = current;
        if (current < CINEMATIC_RENDER_DISTANCE)
            client.options.renderDistance().set(CINEMATIC_RENDER_DISTANCE);
    }

    public static int requiredChunkRadius() {
        return AsterionConfig.INSTANCE.cinematicsEnabled ? REQUIRED_CHUNK_RADIUS : 0;
    }

    public static void begin() {
        Minecraft client = Minecraft.getInstance();
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
        restoreRenderDistance(client);
        CinematicHud.end(client);
    }

    public static void cancelPreparedArrival(Minecraft client) {
        if (!active) restoreRenderDistance(client);
    }

    private static void restoreRenderDistance(Minecraft client) {
        if (previousRenderDistance == null) return;
        if (!client.options.renderDistance().get().equals(previousRenderDistance))
            client.options.renderDistance().set(previousRenderDistance);
        previousRenderDistance = null;
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
        double angle = heading + Mth.lerp(progress, 2.72D, 3.38D);
        double radius = Mth.lerp(progress, 58.0D, 50.0D);
        double height = Math.max(200.0D,
                net.krodark.asterion.worldgen.LabyrinthLevels.MAZE_FLOOR_Y + config.wallHeight + 42.0D)
                + Math.sin(progress * Math.PI) * 8.0D;
        Vec3 railPosition = new Vec3(anchor.x + Math.cos(angle) * radius,
                height, anchor.z + Math.sin(angle) * radius);
        // Overlapping horizontal and vertical motion makes one continuous, bankless dive.
        // Most lateral travel finishes high above the walls; the last approach is almost vertical.
        float dive = smoother((linear - .43F) / .57F);
        double lateral = 1 - Math.pow(1 - dive, 4);
        double downward = Math.pow(dive, 3);
        Vec3 position = railPosition.lerp(new Vec3(basePosition.x, height, basePosition.z), lateral);
        position = new Vec3(position.x, Mth.lerp(downward, height, basePosition.y), position.z);
        // Establish the falling body, reveal the sun, then keep the player framed throughout the dive.
        float sunReveal = smoother((linear - .10F) / .18F);
        float playerFocus = smoother((linear - .43F) / .21F);
        Vec3 focus = basePosition.add(0, -.7, 0).lerp(
                sun.add(0, -config.deadSunSize * .16, 0), sunReveal * (1 - playerFocus));
        Vec3 delta = focus.subtract(position);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float shotYaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float shotPitch = (float)-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        float viewReturn = smoother(Mth.clamp((linear - 0.88F) / 0.12F, 0.0F, 1.0F));
        shotYaw = Mth.rotLerp(viewReturn, shotYaw, returnYaw);
        shotPitch = Mth.lerp(viewReturn, shotPitch, returnPitch);
        // Coherent gusts build during acceleration and settle completely before control returns.
        double wind = Math.sin(Math.PI * smoother((linear - .60F) / .40F));
        double gust = wind * (.035 * Math.sin(time * .19) + .018 * Math.sin(time * .37));
        if (position.y > net.krodark.asterion.worldgen.LabyrinthLevels.MAZE_FLOOR_Y + config.wallHeight + 3)
            position = position.add(gust, gust * .3, -gust * .6);
        shotYaw += (float)(gust * 4 * (1 - viewReturn));
        shotPitch += (float)(wind * .35 * Math.sin(time * .23) * (1 - viewReturn));
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
