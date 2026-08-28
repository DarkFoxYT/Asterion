package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
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
        active = true;
        overworldReady = false;
        ticks = 0;
        fadeTicks = 0;
    }

    public static void tick(Minecraft client) {
        if (!active) return;
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
            if (ticks >= 205) client.player.setDeltaMovement(Vec3.ZERO);
        }
        if (ticks > 305 && client.level != null && client.level.dimension().equals(Level.OVERWORLD)) {
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

    /** The finale deliberately remains attached to the player's body; camera rumble is layered
     * by CameraMixin, while this method avoids any detached-camera positional discontinuity. */
    public static CameraPose cameraPose(Vec3 basePosition, float partialTick) {
        return null;
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tracker) {
        if (!active || ticks < 220) return;
        int color;
        if (overworldReady) {
            int alpha = Math.round((1.0F - smoother(fadeTicks / 52.0F)) * 255.0F);
            color = alpha << 24;
        } else if (ticks < 265) {
            int alpha = Math.round(smoother((ticks - 220) / 45.0F) * 255.0F);
            color = alpha << 24 | 0xFFFFFF;
        } else if (ticks < 292) {
            int shade = Math.round((1.0F - smoother((ticks - 265) / 27.0F)) * 255.0F);
            color = 0xFF000000 | shade << 16 | shade << 8 | shade;
        } else color = 0xFF000000;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color);
    }

    private static void finish(Minecraft client) {
        active = false;
        overworldReady = false;
        fadeTicks = 0;
        ticks = 0;
    }

    private static float smoother(float value) {
        float x = Mth.clamp(value, 0.0F, 1.0F);
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) { }
}
