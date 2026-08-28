package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.event.DeadSunClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Dead Sun detonation, blackout, destination load cover, and controlled fade back to play. */
public final class BossFinaleOverlay {
    private static boolean active;
    private static boolean overworldReady;
    private static int ticks;
    private static int fadeTicks;
    private static float returnYaw;
    private static float returnPitch;
    private static CameraType previousCamera;

    private BossFinaleOverlay() { }

    public static void register() {
        HudElementRegistry.addLast(Asterion.id("boss_finale"), BossFinaleOverlay::render);
    }

    public static void begin() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            returnYaw = client.player.getYRot();
            returnPitch = client.player.getXRot();
        }
        previousCamera = client.options.getCameraType();
        client.options.setCameraType(CameraType.FIRST_PERSON);
        CinematicHud.begin(client);
        active = true;
        overworldReady = false;
        ticks = 0;
        fadeTicks = 0;
    }

    public static void tick(Minecraft client) {
        if (!active) return;
        CinematicHud.maintain(client);
        ticks++;
        if (!overworldReady && client.player != null && client.level != null
                && client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
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
            if (client.options.getCameraType() != CameraType.FIRST_PERSON)
                client.options.setCameraType(CameraType.FIRST_PERSON);
            if (ticks >= 205) client.player.setDeltaMovement(Vec3.ZERO);
        }
        if (ticks > 305 && client.player != null && client.level != null
                && client.level.dimension().equals(Level.OVERWORLD)
                && client.level.hasChunk(client.player.getBlockX() >> 4, client.player.getBlockZ() >> 4)
                && client.level.isLoaded(client.player.blockPosition())) {
            overworldReady = true;
            if (++fadeTicks >= 52) finish(client);
        }
    }

    /** Drives the world-space Dead Sun expansion before the screen is consumed by the flash. */
    public static float sunDetonationStrength() {
        if (!active || overworldReady) return 0.0F;
        return smoother(Mth.clamp((ticks - 18.0F) / 208.0F, 0.0F, 1.0F));
    }

    public static boolean isActive() { return active; }

    /** Exterior crane shot: high enough to clear the arena roof, centered on the Dead Sun, and
     * never blended through the player's body or surrounding walls. */
    public static CameraPose cameraPose(Vec3 basePosition, float partialTick) {
        if (!active || overworldReady || ticks >= 265) return null;
        float progress = smoother(Mth.clamp((ticks + partialTick) / 255.0F, 0.0F, 1.0F));
        double angle = -2.35D + progress * 2.55D;
        double radius = 88.0D + Math.sin(progress * Math.PI) * 28.0D;
        Vec3 position = new Vec3(Math.cos(angle) * radius + 0.5D,
                122.0D + Math.sin(progress * Math.PI) * 28.0D,
                Math.sin(angle) * radius + 0.5D);
        net.krodark.asterion.AsterionConfig config = net.krodark.asterion.AsterionConfig.INSTANCE;
        Vec3 sun = new Vec3(config.deadSunX, config.deadSunHeight, config.deadSunZ);
        Vec3 maze = new Vec3(0.5D, 54.0D, 0.5D);
        Vec3 focus = sun.lerp(maze, 0.34D + progress * 0.24D);
        Vec3 delta = focus.subtract(position);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float)-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        return new CameraPose(position, yaw, pitch);
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tracker) {
        if (!active || ticks < 220) return;
        int color;
        if (overworldReady) {
            int alpha = Math.round((1.0F - smoother(fadeTicks / 52.0F)) * 255.0F);
            color = alpha << 24 | 0xFFFFFF;
        } else if (ticks < 265) {
            int alpha = Math.round(smoother((ticks - 220) / 45.0F) * 255.0F);
            color = alpha << 24 | 0xFFFFFF;
        } else color = 0xFFFFFFFF;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color);
    }

    private static void finish(Minecraft client) {
        active = false;
        overworldReady = false;
        fadeTicks = 0;
        ticks = 0;
        if (previousCamera != null) client.options.setCameraType(previousCamera);
        previousCamera = null;
        CinematicHud.end(client);
        DeadSunClientEvents.clearTransientEffects();
    }

    private static float smoother(float value) {
        float x = Mth.clamp(value, 0.0F, 1.0F);
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) { }
}
