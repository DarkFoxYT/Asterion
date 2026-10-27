package net.krodark.asterion.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class PlayerMessages {
    private PlayerMessages() { }
    public static void overlay(Player player, Component message) {
        //? if <26.1 {
        /*player.displayClientMessage(message, true);
        *///?} else {
        player.sendOverlayMessage(message);
        //?}
    }
}
