package net.krodark.asterion.client.render;

import net.minecraft.client.Minecraft;

public final class HudVisibility {
    private HudVisibility() { }
    public static boolean hidden(Minecraft client) {
        //? if >=26.2 {
        /*return client.gui.hud.isHidden();
        *///?} else {
        return client.options.hideGui;
        //?}
    }
    public static void hidden(Minecraft client, boolean hidden) {
        //? if >=26.2 {
        /*if (client.gui.hud.isHidden() != hidden) client.gui.hud.toggle();
        *///?} else {
        client.options.hideGui = hidden;
        //?}
    }
}
