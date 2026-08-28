package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.event.DeadSunClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** A short event title only; the Eclipse visuals live in the world shaders. */
public final class EclipseOverlay {
    private static final Component TITLE = Component.translatable("event.asterion.eclipse.title");

    private EclipseOverlay() { }

    public static void register() {
        HudElementRegistry.addLast(Asterion.id("eclipse_overlay"), EclipseOverlay::render);
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        float strength = DeadSunClientEvents.eclipseStrength();
        if (strength <= 0.001F) return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        int intro = DeadSunClientEvents.eclipseIntroTicks();
        if (intro <= 120) {
            float appear = Mth.clamp(intro / 20.0F, 0.0F, 1.0F);
            float vanish = Mth.clamp((120 - intro) / 40.0F, 0.0F, 1.0F);
            int alpha = Mth.clamp(Math.round(Math.min(appear, vanish) * 235.0F), 0, 235);
            graphics.centeredText(client.font, TITLE, width / 2, height / 3,
                    alpha << 24 | 0xD82014);
        }
    }
}
