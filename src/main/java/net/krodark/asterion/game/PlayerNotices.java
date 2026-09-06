package net.krodark.asterion.game;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerNotices {
    private PlayerNotices() { }

    public static void show(net.minecraft.world.entity.player.Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer)
            serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(message));
    }
}
