package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.TransitionReadyPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

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
        DeadSunEntryCinematic.prepareForArrival(Minecraft.getInstance());
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
        if (stableTicks >= REQUIRED_STABLE_TICKS) {
            fadingOut = true;
        }
    }

    private static boolean destinationChunksReady(Minecraft client) {
        if (client.screen instanceof LevelLoadingScreen || client.level == null || client.player == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return false;
        int centerX = client.player.getBlockX() >> 4;
        int centerZ = client.player.getBlockZ() >> 4;
        int radius = DeadSunEntryCinematic.requiredChunkRadius();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                if (!client.level.hasChunk(centerX + dx, centerZ + dz)) return false;
            }
        return client.level.isLoaded(client.player.blockPosition());
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

    public static boolean shouldReplaceLoadingScreen() {
        Minecraft client = Minecraft.getInstance();
        return active || client.level != null
                && client.level.dimension().equals(Asterion.ASTERION_LEVEL);
    }

    public static void renderLoadingScreen(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        if (BossFinaleOverlay.isActive()) return;
        renderDescent(graphics, 255);
    }

    private static void renderHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (!active) return;
        int alpha = fadingOut
                ? Math.max(0, 255 - Math.round(fadeOutProgress / (float) FADE_OUT_TICKS * 255.0F))
                : 255;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
        if (!fadingOut || alpha > 90) renderDescent(graphics, alpha);
    }

    private static void renderDescent(GuiGraphicsExtractor graphics, int alpha) {
        Minecraft client = Minecraft.getInstance();
        long frame = totalTicks;
        float pulse = 0.5F + 0.5F * Mth.sin(frame * 0.19F);
        int edgeAlpha = Math.round(alpha * (0.10F + pulse * 0.08F));
        int edge = Math.max(18, graphics.guiWidth() / 12);
        graphics.fill(0, 0, edge, graphics.guiHeight(), edgeAlpha << 24 | 0x5A0505);
        graphics.fill(graphics.guiWidth() - edge, 0, graphics.guiWidth(), graphics.guiHeight(),
                edgeAlpha << 24 | 0x5A0505);

        String text = DESCENDING.getString();
        int totalWidth = client.font.width(text);
        int cursor = (graphics.guiWidth() - totalWidth) / 2;
        int baseY = graphics.guiHeight() / 2 - 3;
        for (int index = 0; index < text.length(); index++) {
            String letter = text.substring(index, index + 1);
            int width = client.font.width(letter);
            long noise = mix(frame * 0x9E3779B97F4A7C15L + index * 0xD1B54A32D192ED03L);
            boolean twitch = (noise & 31L) == 0L;
            int jitterX = twitch ? (int)((noise >>> 9) % 3L) - 1 : 0;
            int jitterY = twitch ? (int)((noise >>> 13) % 3L) - 1 : 0;
            int brightness = twitch ? 255 : 205 + Math.round(pulse * 24.0F);
            graphics.centeredText(client.font, Component.literal(letter),
                    cursor + width / 2 + jitterX, baseY + jitterY,
                    alpha << 24 | brightness << 16 | 38 << 8 | 30);
            cursor += width;
        }
        int lineWidth = Math.round(totalWidth * (0.35F + pulse * 0.22F));
        int center = graphics.guiWidth() / 2;
        graphics.fill(center - lineWidth / 2, baseY + 13, center + lineWidth / 2, baseY + 14,
                Math.round(alpha * 0.62F) << 24 | 0xA40A08);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
