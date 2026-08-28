package net.krodark.asterion.client;

import net.minecraft.client.Minecraft;

/** Owns vanilla HUD suppression without losing the player's prior F1 preference. */
public final class CinematicHud {
    private static boolean hidden;
    private static boolean previousHideGui;

    private CinematicHud() { }

    public static void begin(Minecraft client) {
        if (!hidden) previousHideGui = client.options.hideGui;
        hidden = true;
        client.options.hideGui = true;
    }

    public static void maintain(Minecraft client) {
        if (hidden) client.options.hideGui = true;
    }

    public static void end(Minecraft client) {
        if (!hidden) return;
        hidden = false;
        client.options.hideGui = previousHideGui;
    }

    public static boolean isHidden() {
        return hidden;
    }
}
