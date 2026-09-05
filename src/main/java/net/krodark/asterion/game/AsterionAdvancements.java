package net.krodark.asterion.game;

import net.krodark.asterion.Asterion;
import net.minecraft.server.level.ServerPlayer;

/** Event-driven milestones; vanilla owns persistence, notifications and one-time rewards. */
public final class AsterionAdvancements {
    private AsterionAdvancements() {}

    public static void award(ServerPlayer player, String milestone) {
        var advancement = player.level().getServer().getAdvancements().get(Asterion.id(milestone));
        if (advancement != null) player.getAdvancements().award(advancement, "milestone");
    }

    public static void queenProgress(ServerPlayer player, int completed) {
        if (completed >= 1) award(player, "queens_favor");
        if (completed >= 10) award(player, "trusted_supplier");
        if (completed >= 21) award(player, "queens_covenant");
    }
}
