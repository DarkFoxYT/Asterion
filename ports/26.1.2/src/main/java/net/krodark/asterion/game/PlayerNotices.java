package net.krodark.asterion.game;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerNotices {
    private PlayerNotices() { }

    public static void success(net.minecraft.commands.CommandSourceStack source,
                               java.util.function.Supplier<Component> message, boolean broadcast) {
        if (source.getEntity() instanceof ServerPlayer player) show(player, message.get());
        else source.sendSuccess(message, false);
    }

    public static void failure(net.minecraft.commands.CommandSourceStack source, Component message) {
        if (source.getEntity() instanceof ServerPlayer player) show(player, message);
        else source.sendFailure(message);
    }

    public static void show(net.minecraft.world.entity.player.Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer)
            serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(message));
    }
}
