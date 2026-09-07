package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.client.cinematic.*;
import net.krodark.asterion.client.forge.CrucibleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Keeps recorded gameplay UI and camera control out of replay playback/export. */
public final class ReplayCompatibility {
    private ReplayCompatibility() { }
    public static void addHud(Identifier id, HudElement element) {
        HudElementRegistry.addLast(id, (graphics, tracker) -> {
            if (!AsterionClient.isPlayback(Minecraft.getInstance())) element.extractRenderState(graphics, tracker);
        });
    }
    public static void cancelCinematics(Minecraft client) {
        BossEntranceCinematic.finish(client);
        CursedBrazierCinematic.finish(client);
        RoofCollapseCinematic.finish(client);
        if (DeadSunEntryCinematic.isActive()) DeadSunEntryCinematic.finish(client);
        if (BossFinaleOverlay.isActive()) BossFinaleOverlay.finish(client);
        DimensionTransitionOverlay.cancel();
        CrucibleCamera.cancel(client);
        CinematicHud.end(client);
        if (client.screen instanceof CrucibleScreen) client.setScreen(null);
    }
}
