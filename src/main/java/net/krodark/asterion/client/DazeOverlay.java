package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.krodark.asterion.network.DazePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.core.particles.ParticleTypes;
import org.lwjgl.glfw.GLFW;

public final class DazeOverlay {
    private static int remaining;
    private static int duration;
    private static int required;
    private static int progress;
    private static boolean spaceWasDown;
    private static boolean ragdollRecovery;
    private static int ragdollProgress;
    private static int ragdollRequired = 4;

    private DazeOverlay() { }

    public static void register() {
        HudElementRegistry.addLast(Asterion.id("daze_escape"), DazeOverlay::render);
    }

    public static void begin(DazePayload payload) {
        duration = Mth.clamp(payload.durationTicks(), 30, 200);
        remaining = duration;
        required = Mth.clamp(payload.requiredPresses(), 4, 18);
        progress = 0;
        spaceWasDown = true;
    }

    public static void tick(Minecraft client) {
        if (remaining <= 0 || client.player == null || client.level == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)
                || !DismembermentEngine.INSTANCE.isPlayerTumbling(client.player.getId())) {
            remaining = 0;
            return;
        }
        remaining--;
        spawnStunOrbit(client);
        boolean down = client.screen == null && GLFW.glfwGetKey(client.getWindow().handle(),
                GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        boolean mash = AsterionConfig.INSTANCE.ragdollMashRecovery;
        // Mash progress is made of completed key presses and stays earned. The newer
        // per-tick decay made the bar immediately retreat between every legitimate tap.
        if (mash && down && !spaceWasDown) progress = Math.min(required, progress + 1);
        else if (!mash && down && (duration - remaining) % 3 == 0)
            progress = Math.min(required, progress + 1);
        if (progress >= required || remaining <= 0) {
            net.krodark.asterion.client.ragdoll.RagdollClientController
                    .suppressAutomaticFallRagdoll(40);
            DismembermentEngine.INSTANCE.releaseRagdoll(client.player.getId());
            remaining = 0;
        }
        spaceWasDown = down;
    }

    private static void spawnStunOrbit(Minecraft client) {
        if ((client.player.tickCount & 1) != 0) return;
        double time = (duration - remaining) * 0.24D;
        int stars = duration >= 100 ? 4 : 3;
        for (int i = 0; i < stars; i++) {
            double angle = time + i * Mth.TWO_PI / stars;
            double radius = duration >= 100 ? 0.62D : 0.48D;
            double x = client.player.getX() + Math.cos(angle) * radius;
            double y = client.player.getEyeY() + 0.34D + Math.sin(time * 0.65D + i) * 0.08D;
            double z = client.player.getZ() + Math.sin(angle) * radius;
            client.level.addParticle(i % 2 == 0 ? ParticleTypes.WAX_ON : ParticleTypes.ELECTRIC_SPARK,
                    x, y, z, -Math.sin(angle) * 0.025D, 0.006D, Math.cos(angle) * 0.025D);
        }
    }

    public static boolean isActive() { return remaining > 0; }

    public static void showRagdollRecovery(int presses, int needed) {
        ragdollRecovery = true;
        ragdollRequired = Math.max(1, needed);
        ragdollProgress = Mth.clamp(presses, 0, ragdollRequired);
    }

    public static void hideRagdollRecovery() {
        ragdollRecovery = false;
        ragdollProgress = 0;
    }

    public static void cancel() {
        remaining = 0;
        progress = 0;
        spaceWasDown = false;
        hideRagdollRecovery();
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        if (remaining <= 0 && !ragdollRecovery) return;
        Minecraft client = Minecraft.getInstance();
        int width = 190, height = 12;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 72;
        float intro = remaining > 0 ? Mth.clamp((duration - remaining) / 8.0F, 0.0F, 1.0F) : 1.0F;
        int alpha = Mth.floor(intro * 220.0F);
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, alpha << 24 | 0x140B0B);
        graphics.fill(x, y, x + width, y + height, alpha << 24 | 0x2B2222);
        int shownProgress = remaining > 0 ? progress : ragdollProgress;
        int shownRequired = remaining > 0 ? required : ragdollRequired;
        int filled = Mth.floor(width * Mth.clamp(shownProgress / (float) shownRequired, 0.0F, 1.0F));
        graphics.fill(x, y, x + filled, y + height, alpha << 24 | 0xC92820);
        String instruction = remaining <= 0 ? "MASH SPACE — GET UP"
                : AsterionConfig.INSTANCE.ragdollMashRecovery
                ? "TAP SPACE — BREAK THE FALL" : "HOLD SPACE — BRACE AND GET UP";
        graphics.centeredText(client.font, Component.literal(instruction),
                graphics.guiWidth() / 2, y - 13, alpha << 24 | 0xFFE3D8);
    }
}
