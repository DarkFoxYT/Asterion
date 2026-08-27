package net.krodark.labyrinth.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.labyrinth.Labyrinth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class DimensionTransitionOverlay {
    private static final Component DESCENDING = Component.translatable("transition.labyrinth.descending");
    private static int fadeInTicks = 12;
    private static int holdTicks = 8;
    private static int fadeProgress;
    private static int arrivalHold;
    private static boolean active;
    private static boolean destinationVisible;
    private static int totalTicks;

    private DimensionTransitionOverlay() {
    }

    public static void register() {
        HudElementRegistry.addLast(Labyrinth.id("dimension_transition"), DimensionTransitionOverlay::renderHud);
    }

    public static void begin(int requestedFadeIn, int requestedHold) {
        fadeInTicks = Math.max(1, requestedFadeIn);
        holdTicks = Math.max(0, requestedHold);
        fadeProgress = 0;
        arrivalHold = 0;
        destinationVisible = false;
        totalTicks = 0;
        active = true;
    }

    public static void tick(Minecraft client) {
        if (!active) return;
        if (++totalTicks > 160 || client.player == null) {
            clear();
            return;
        }
        if (fadeProgress < fadeInTicks) {
            fadeProgress++;
            return;
        }
        if (!destinationVisible) {
            boolean loading = client.screen instanceof LevelLoadingScreen;
            destinationVisible = !loading && client.level != null
                    && client.level.dimension().equals(Labyrinth.LABYRINTH_LEVEL);
            return;
        }
        if (arrivalHold++ < holdTicks) return;
        if (--fadeProgress <= 0) {
            fadeProgress = 0;
            active = false;
        }
    }

    private static void clear() {
        fadeProgress = 0;
        arrivalHold = 0;
        totalTicks = 0;
        destinationVisible = false;
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static void renderLoadingScreen(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        Minecraft client = Minecraft.getInstance();
        graphics.centeredText(client.font, DESCENDING, graphics.guiWidth() / 2,
                graphics.guiHeight() / 2, 0xFFD2D6DE);
    }

    private static void renderHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (!active) return;
        float linear = Mth.clamp(fadeProgress / (float) fadeInTicks, 0.0f, 1.0f);
        float smooth = linear * linear * (3.0f - 2.0f * linear);
        int alpha = Mth.clamp(Math.round(smooth * 255.0f), 0, 255);
        if (alpha <= 0) return;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
        if (alpha > 170) {
            int textAlpha = Mth.clamp((alpha - 150) * 2, 0, 255);
            graphics.centeredText(Minecraft.getInstance().font, DESCENDING,
                    graphics.guiWidth() / 2, graphics.guiHeight() / 2,
                    textAlpha << 24 | 0xD2D6DE);
        }
    }
}
