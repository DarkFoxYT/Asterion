package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Clean arrival objective: introduced after the landing tumble, then retained top-left. */
public final class MazeObjectiveOverlay {
    private static final Component INTRO = Component.translatable("objective.asterion.new");
    private static final Component REACH_CENTER = Component.translatable("objective.asterion.reach_center");
    private static boolean armed;
    private static boolean sawTumble;
    private static boolean visible;
    private static int waitTicks;
    private static int visibleTicks;

    private MazeObjectiveOverlay() { }

    public static void register() {
        HudElementRegistry.addLast(Asterion.id("maze_objective"), MazeObjectiveOverlay::render);
    }

    public static void armAfterArrival() {
        armed = true;
        sawTumble = false;
        visible = false;
        waitTicks = 0;
        visibleTicks = 0;
    }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            armed = visible = false;
            return;
        }
        boolean tumbling = DismembermentEngine.INSTANCE.isPlayerTumbling(client.player.getId());
        if (armed) {
            waitTicks++;
            sawTumble |= tumbling;
            if (waitTicks >= 20 && !tumbling && (sawTumble || waitTicks >= 100)) {
                armed = false;
                visible = true;
            }
        }
        if (!visible) return;
        visibleTicks++;
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tracker) {
        if (!visible) return;
        Minecraft client = Minecraft.getInstance();
        float renderTicks = visibleTicks + Mth.clamp(tracker.getGameTimeDeltaPartialTick(false), 0.0F, 1.0F);
        float appear = smootherstep(Mth.clamp(renderTicks / 16.0F, 0.0F, 1.0F));
        int alpha = Math.round(appear * 245.0F);
        int width = client.font.width(REACH_CENTER);
        float settle = smootherstep(Mth.clamp((renderTicks - 28.0F) / 58.0F, 0.0F, 1.0F));
        int centerX = Math.round(Mth.lerp(settle, graphics.guiWidth() * 0.5F, 14.0F + width * 0.5F));
        int panelTop = Math.round(Mth.lerp(settle, graphics.guiHeight() * 0.32F - 12.0F, 10.0F));
        int panelWidth = Math.round(Mth.lerp(settle, 210.0F, width + 12.0F));
        int panelHeight = Math.round(Mth.lerp(settle, 48.0F, 19.0F));
        int left = centerX - panelWidth / 2;
        int textY = Math.round(Mth.lerp(settle, panelTop + 19.0F, 16.0F));
        graphics.fill(left, panelTop, left + panelWidth, panelTop + panelHeight,
                Math.round(appear * 174.0F) << 24 | 0x07110E);
        graphics.fill(left, panelTop, left + 3, panelTop + panelHeight, alpha << 24 | 0x79C9A0);
        int introAlpha = Math.round(alpha * (1.0F - smootherstep(Mth.clamp(settle / 0.72F, 0.0F, 1.0F))));
        if (introAlpha > 2)
            graphics.centeredText(client.font, INTRO, centerX, textY - 11, introAlpha << 24 | 0x91D6B4);
        graphics.centeredText(client.font, REACH_CENTER, centerX, textY, alpha << 24 | 0xF4E7C4);
    }

    private static float smoothstep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static float smootherstep(float value) {
        return value * value * value * (value * (value * 6.0F - 15.0F) + 10.0F);
    }
}
