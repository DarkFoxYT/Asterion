package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.krodark.asterion.network.DazePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Space-mash recovery shown only for forced combat ragdolls. */
public final class DazeOverlay {
    private static int remaining;
    private static int duration;
    private static int required;
    private static float progress;
    private static boolean spaceWasDown;

    private DazeOverlay() { }

    public static void register() {
        HudElementRegistry.addLast(Asterion.id("daze_escape"), DazeOverlay::render);
    }

    public static void begin(DazePayload payload) {
        duration = Mth.clamp(payload.durationTicks(), 30, 200);
        remaining = duration;
        required = Mth.clamp(payload.requiredPresses(), 4, 18);
        progress = 0.0F;
        spaceWasDown = true;
    }

    public static void tick(Minecraft client) {
        if (remaining <= 0 || client.player == null || client.level == null) {
            remaining = 0;
            return;
        }
        remaining--;
        boolean down = client.screen == null && GLFW.glfwGetKey(client.getWindow().handle(),
                GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        if (down && !spaceWasDown) {
            progress = Math.min(required, progress + 1.0F);
            if (progress >= required) {
                DismembermentEngine.INSTANCE.releaseRagdoll(client.player.getId());
                remaining = 0;
            }
        }
        spaceWasDown = down;
    }

    public static boolean isActive() { return remaining > 0; }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        if (remaining <= 0) return;
        Minecraft client = Minecraft.getInstance();
        int width = 190, height = 12;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 72;
        float intro = Mth.clamp((duration - remaining) / 8.0F, 0.0F, 1.0F);
        int alpha = Mth.floor(intro * 220.0F);
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, alpha << 24 | 0x140B0B);
        graphics.fill(x, y, x + width, y + height, alpha << 24 | 0x2B2222);
        int filled = Mth.floor(width * Mth.clamp(progress / required, 0.0F, 1.0F));
        graphics.fill(x, y, x + filled, y + height, alpha << 24 | 0xC92820);
        graphics.centeredText(client.font, Component.literal("MASH SPACE — GET UP"),
                graphics.guiWidth() / 2, y - 13, alpha << 24 | 0xFFE3D8);
    }
}
