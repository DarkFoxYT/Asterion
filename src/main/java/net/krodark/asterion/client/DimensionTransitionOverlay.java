package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.TransitionReadyPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.chat.Component;

public final class DimensionTransitionOverlay {
    private static final Component DESCENDING = Component.translatable("transition.asterion.descending");
    private static final int REQUIRED_STABLE_TICKS = 8;
    private static final int FADE_OUT_TICKS = 5;
    private static int stableTicks;
    private static int fadeOutProgress;
    private static boolean active;
    private static boolean fadingOut;
    private static boolean readySent;
    private static int totalTicks;

    private DimensionTransitionOverlay() {
    }

    public static void register() {
        HudElementRegistry.addLast(Asterion.id("dimension_transition"), DimensionTransitionOverlay::renderHud);
    }

    public static void begin(int requestedFadeIn, int requestedHold) {
        stableTicks = 0;
        fadeOutProgress = 0;
        fadingOut = false;
        readySent = false;
        totalTicks = 0;
        active = true;
    }

    public static void tick(Minecraft client) {
        if (!active) return;
        totalTicks++;
        if (fadingOut) {
            if (++fadeOutProgress >= FADE_OUT_TICKS) {
                sendReady();
                clear();
            }
            return;
        }

        boolean ready = destinationChunksReady(client);
        stableTicks = ready ? stableTicks + 1 : 0;
        if (stableTicks >= REQUIRED_STABLE_TICKS || totalTicks >= 200) {
            fadingOut = true;
        }
    }

    private static boolean destinationChunksReady(Minecraft client) {
        if (client.screen instanceof LevelLoadingScreen || client.level == null || client.player == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return false;
        // The server already holds a 3x3 safety buffer. Requiring all nine packets here can
        // deadlock the cosmetic overlay behind slow optional neighbors; the landing chunk is
        // sufficient to render the player and floor while the rest streams under the fog.
        int centerX = client.player.getBlockX() >> 4;
        int centerZ = client.player.getBlockZ() >> 4;
        return client.level.hasChunk(centerX, centerZ)
                && client.level.isLoaded(client.player.blockPosition());
    }

    private static void sendReady() {
        if (readySent || !ClientPlayNetworking.canSend(TransitionReadyPayload.TYPE)) return;
        ClientPlayNetworking.send(TransitionReadyPayload.INSTANCE);
        readySent = true;
    }

    private static void clear() {
        DeadSunEntryCinematic.begin();
        MazeObjectiveOverlay.armAfterArrival();
        stableTicks = 0;
        fadeOutProgress = 0;
        totalTicks = 0;
        fadingOut = false;
        readySent = false;
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    /** Never expose vanilla's terrain screen for this dimension, including the few frames after
     * our ready packet is sent while Minecraft is still dismissing its own receiving screen. */
    public static boolean shouldReplaceLoadingScreen() {
        Minecraft client = Minecraft.getInstance();
        return active || client.level != null
                && client.level.dimension().equals(Asterion.ASTERION_LEVEL);
    }

    public static void renderLoadingScreen(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        if (BossFinaleOverlay.isActive()) return;
        Minecraft client = Minecraft.getInstance();
        graphics.centeredText(client.font, DESCENDING, graphics.guiWidth() / 2,
                graphics.guiHeight() / 2, 0xFFD2D6DE);
    }

    private static void renderHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (!active) return;
        int alpha = fadingOut
                ? Math.max(0, 255 - Math.round(fadeOutProgress / (float) FADE_OUT_TICKS * 255.0F))
                : 255;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
        if (!fadingOut || alpha > 170) {
            int textAlpha = Math.min(255, alpha);
            graphics.centeredText(Minecraft.getInstance().font, DESCENDING,
                    graphics.guiWidth() / 2, graphics.guiHeight() / 2,
                    textAlpha << 24 | 0xD2D6DE);
        }
    }
}
