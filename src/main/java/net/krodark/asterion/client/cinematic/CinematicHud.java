package net.krodark.asterion.client.cinematic;

import net.minecraft.client.Minecraft;

public final class CinematicHud {
    private static boolean hidden;
    private static boolean previousHideGui;

    private CinematicHud() { }

    public static void begin(Minecraft client) {
        if (!hidden) previousHideGui = net.krodark.asterion.client.render.HudVisibility.hidden(client);
        hidden = true;
        net.krodark.asterion.client.render.HudVisibility.hidden(client, true);
    }

    public static void maintain(Minecraft client) {
        if (hidden) net.krodark.asterion.client.render.HudVisibility.hidden(client, true);
    }

    public static void end(Minecraft client) {
        if (!hidden) return;
        hidden = false;
        net.krodark.asterion.client.render.HudVisibility.hidden(client, previousHideGui);
    }

    public static boolean isHidden() {
        return hidden;
    }
}
