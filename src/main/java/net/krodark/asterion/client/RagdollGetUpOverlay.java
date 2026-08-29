package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Contextual recovery hint shown while the local player is physically ragdolled. */
public final class RagdollGetUpOverlay {
    private static final Component MESSAGE = Component.translatable("hud.asterion.ragdoll_get_up");

    private RagdollGetUpOverlay() { }

    public static void register() {
        HudElementRegistry.addLast(Asterion.id("ragdoll_get_up"), RagdollGetUpOverlay::render);
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.screen != null
                || CinematicHud.isHidden() || DazeOverlay.isActive()) return;
        DismembermentEngine engine = DismembermentEngine.INSTANCE;
        if (!engine.isPlayerTumbling(client.player.getId())) return;

        float age = engine.ragdollElapsedTicks(client.player.getId())
                + Mth.clamp(delta.getGameTimeDeltaPartialTick(false), 0.0F, 1.0F);
        float appear = Mth.clamp((age - 3.0F) / 10.0F, 0.0F, 1.0F);
        float pulse = 0.88F + 0.12F * Mth.sin(age * 0.14F);
        int alpha = Math.round(appear * pulse * 245.0F);
        int textWidth = client.font.width(MESSAGE);
        int centerX = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 54;
        int halfWidth = Math.max(58, textWidth / 2 + 13);

        graphics.fill(centerX - halfWidth, y - 7, centerX + halfWidth, y + 14,
                Math.round(appear * 150.0F) << 24 | 0x080606);
        graphics.fill(centerX - halfWidth, y + 13, centerX + halfWidth, y + 14,
                Math.round(appear * 180.0F) << 24 | 0x9E3028);
        graphics.centeredText(client.font, MESSAGE, centerX, y,
                alpha << 24 | 0xF4E6D8);
    }
}
