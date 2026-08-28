package net.krodark.asterion.client;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A short, fully client-authored arrival shot. It never moves or teleports the player. */
public final class DeadSunEntryCinematic {
    private static final int END_TICKS = 190;
    private static final int CINEMATIC_RENDER_DISTANCE = 12;
    private static final int REQUIRED_CHUNK_RADIUS = 6;
    private static boolean active;
    private static int ticks;
    private static float returnYaw;
    private static float returnPitch;
    private static CameraType previousCamera;
    private static Integer previousRenderDistance;
    private static Boolean previousSmartCull;

    private DeadSunEntryCinematic() { }

    /**
     * Raises view distance before the black arrival veil opens. The detached camera travels four
     * chunks away from the body, so relying on the gameplay setting would expose its chunk edge.
     */
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
        // Drop any graph asynchronously derived from the player-side camera before the detached
        // rail starts. LevelRenderer will rebuild its view area from the cinematic Camera position.
        client.levelRenderer.getSectionOcclusionGraph().invalidate();
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
        // Use Minecraft's native switch instead of redirecting LevelRenderer bytecode. This is
        // compatible with Sodium, which replaces the same terrain-culling method.
        client.smartCull = false;
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

    public static CameraPose cameraPose(Vec3 basePosition, float partialTick) {
        if (!active) return null;
        float time = ticks + partialTick;
        float linear = Mth.clamp(time / END_TICKS, 0.0F, 1.0F);
        float progress = smoother(linear);
        AsterionConfig config = AsterionConfig.INSTANCE;
        Vec3 sun = new Vec3(config.deadSunX, config.deadSunHeight, config.deadSunZ);
        Vec3 towardSun = sun.subtract(basePosition);
        double heading = Mth.atan2(towardSun.z, towardSun.x);
        // The whole rail stays above the authored roof height. Arrival keeps a dedicated chunk
        // envelope around this rail, so low gameplay render distances never expose maze edges.
        // Stay behind the falling player relative to the Sun, keeping the ragdoll as foreground
        // silhouette while the Dead Sun remains the distant anchor of the shot.
        double angle = heading + Mth.lerp(progress, 2.58D, 3.62D);
        double radius = Mth.lerp(progress, 62.0D, 48.0D);
        double height = 118.0D + Math.sin(progress * Math.PI) * 20.0D;
        Vec3 railPosition = new Vec3(basePosition.x + Math.cos(angle) * radius,
                height, basePosition.z + Math.sin(angle) * radius);
        float returnWeight = smoother(Mth.clamp((linear - 0.78F) / 0.22F, 0.0F, 1.0F));
        Vec3 position = railPosition.lerp(basePosition, returnWeight);
        Vec3 localMaze = new Vec3(basePosition.x, 68.0D, basePosition.z);
        // Establish the square maze, ease onto the Dead Sun, then hold the celestial target.
        // The small downward offset keeps its full corona in frame instead of centering the disc
        // so tightly that the player sees nothing but its surface.
        float sunFocus = smoother(Mth.clamp((linear - 0.08F) / 0.34F, 0.0F, 1.0F));
        Vec3 focus = localMaze.lerp(sun.add(0.0D, -config.deadSunSize * 0.16D, 0.0D),
                Mth.lerp(sunFocus, 0.60D, 0.94D));
        Vec3 delta = focus.subtract(position);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float shotYaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float shotPitch = (float)-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        float viewReturn = smoother(Mth.clamp((linear - 0.90F) / 0.10F, 0.0F, 1.0F));
        shotYaw = Mth.rotLerp(viewReturn, shotYaw, returnYaw);
        shotPitch = Mth.lerp(viewReturn, shotPitch, returnPitch);
        float radiance = radianceStrength();
        if (radiance > 0.001F) {
            double shake = radiance * (0.13D * Math.sin(time * 1.73D)
                    + 0.07D * Math.sin(time * 0.61D + 1.4D));
            position = position.add(shake, shake * 0.42D, -shake * 0.76D);
            shotYaw += (float)(shake * 0.34D);
            shotPitch += (float)(shake * 0.20D);
        }
        return new CameraPose(position, shotYaw, shotPitch);
    }

    public static float radianceStrength() {
        if (!active) return 0.0F;
        float time = ticks;
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
