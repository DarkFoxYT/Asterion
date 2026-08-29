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

public final class BossFinaleOverlay {
    private static final int CINEMATIC_RENDER_DISTANCE = 12;
    private static boolean active;
    private static boolean overworldReady;
    private static int ticks;
    private static int fadeTicks;
    private static float returnYaw;
    private static float returnPitch;
    private static CameraType previousCamera;
    private static Boolean previousSmartCull;
    private static Integer previousRenderDistance;

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
        previousSmartCull = client.smartCull;
        previousRenderDistance = client.options.renderDistance().get();
        client.smartCull = false;
        if (previousRenderDistance < CINEMATIC_RENDER_DISTANCE)
            client.options.renderDistance().set(CINEMATIC_RENDER_DISTANCE);
        client.options.setCameraType(CameraType.FIRST_PERSON);
        CinematicHud.begin(client);
        if (client.level != null) client.levelRenderer.getSectionOcclusionGraph().invalidate();
        active = true;
        overworldReady = false;
        ticks = 0;
        fadeTicks = 0;
    }

    public static void tick(Minecraft client) {
        if (!active) return;
        if (client.player == null || client.level == null) {
            finish(client);
            return;
        }
        CinematicHud.maintain(client);
        client.smartCull = false;
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
        if (ticks >= 216) client.options.hideGui = false;
        if (ticks > 305 && client.player != null && client.level != null
                && client.level.dimension().equals(Level.OVERWORLD)
                && client.level.hasChunk(client.player.getBlockX() >> 4, client.player.getBlockZ() >> 4)
                && client.level.isLoaded(client.player.blockPosition())) {
            overworldReady = true;
            if (++fadeTicks >= 52) finish(client);
        }
    }

    public static float sunDetonationStrength() {
        if (!active || overworldReady) return 0.0F;
        return smoother(Mth.clamp((ticks - 18.0F) / 208.0F, 0.0F, 1.0F));
    }

    public static boolean isActive() { return active; }

    public static CameraPose cameraPose(Vec3 basePosition, float partialTick) {
        if (!active || overworldReady || ticks >= 265) return null;
        float time = ticks + partialTick;
        float progress = smoother(Mth.clamp(time / 255.0F, 0.0F, 1.0F));
        double angle = -2.48D + progress * 3.08D + Math.sin(progress * Math.PI * 3.0D) * 0.075D;
        double radius = Mth.lerp(progress, 104.0D, 70.0D)
                + Math.sin(progress * Math.PI) * 34.0D;
        Vec3 position = new Vec3(Math.cos(angle) * radius + 0.5D,
                116.0D + Math.sin(progress * Math.PI) * 42.0D - progress * 12.0D,
                Math.sin(angle) * radius + 0.5D);
        net.krodark.asterion.AsterionConfig config = net.krodark.asterion.AsterionConfig.INSTANCE;
        Vec3 sun = new Vec3(config.deadSunX, config.deadSunHeight, config.deadSunZ);
        Vec3 maze = new Vec3(0.5D, 54.0D, 0.5D);
        Vec3 focus = sun.lerp(maze, 0.18D + progress * 0.48D);
        Vec3 delta = focus.subtract(position);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float)-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        float chaos = sunDetonationStrength();
        double shakeX = (Math.sin(time * 1.73D) + Math.sin(time * 0.37D + 1.8D) * 0.45D)
                * chaos * 0.46D;
        double shakeY = (Math.sin(time * 2.11D + 0.6D) + Math.sin(time * 0.51D) * 0.36D)
                * chaos * 0.28D;
        position = position.add(shakeX, shakeY, -shakeX * 0.62D);
        yaw += (float)(shakeX * 0.42D);
        pitch += (float)(shakeY * 0.34D);
        return new CameraPose(position, yaw, pitch);
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tracker) {
        if (!active || ticks < 220) return;
        if (overworldReady) {
            int alpha = Math.round((1.0F - smoother(fadeTicks / 52.0F)) * 255.0F);
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
            return;
        }
        float blackout = smoother(Mth.clamp((ticks - 220.0F) / 45.0F, 0.0F, 1.0F));
        int red = Mth.floor(Mth.lerp(blackout, 52.0F, 2.0F));
        int green = Mth.floor(Mth.lerp(blackout, 1.0F, 0.0F));
        int alpha = Math.round(blackout * 255.0F);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                alpha << 24 | red << 16 | green << 8);
        float flash = 1.0F - Mth.clamp(Math.abs(ticks - 224.0F) / 7.0F, 0.0F, 1.0F);
        if (flash > 0.0F) {
            int flashAlpha = Math.round(smoother(flash) * 190.0F);
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    flashAlpha << 24 | 0xD43A24);
        }
    }

    private static void finish(Minecraft client) {
        active = false;
        overworldReady = false;
        fadeTicks = 0;
        ticks = 0;
        if (previousCamera != null) client.options.setCameraType(previousCamera);
        previousCamera = null;
        if (previousSmartCull != null) client.smartCull = previousSmartCull;
        previousSmartCull = null;
        if (previousRenderDistance != null
                && !client.options.renderDistance().get().equals(previousRenderDistance))
            client.options.renderDistance().set(previousRenderDistance);
        previousRenderDistance = null;
        if (client.level != null) client.levelRenderer.getSectionOcclusionGraph().invalidate();
        CinematicHud.end(client);
        DeadSunClientEvents.clearTransientEffects();
    }

    private static float smoother(float value) {
        float x = Mth.clamp(value, 0.0F, 1.0F);
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) { }
}
