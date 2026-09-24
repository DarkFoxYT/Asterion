package net.krodark.asterion.event;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.LimboSeaEventPayload;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** Manual, synced overrides for the two otherwise periodic Limbo sea events. */
public final class LimboSeaCommands {
    public static final long NATURAL = -1, STOPPED = -2;
    private static long tempestStart = NATURAL, whirlpoolStart = NATURAL;
    private LimboSeaCommands() { }

    public static long tempestStart() { return tempestStart; }
    public static long whirlpoolStart() { return whirlpoolStart; }
    public static void receive(LimboSeaEventPayload state) {
        receive(state.tempestStart(), state.whirlpoolStart());
    }
    public static void receive(long tempest, long whirlpool) {
        tempestStart = tempest; whirlpoolStart = whirlpool;
    }
    public static void sync(ServerLevel level) {
        var state = new LimboSeaEventPayload(tempestStart, whirlpoolStart);
        for (var player : level.players()) if (ServerPlayNetworking.canSend(player, LimboSeaEventPayload.TYPE))
            ServerPlayNetworking.send(player, state);
    }
    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> receive(new LimboSeaEventPayload(NATURAL, NATURAL)));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("limboevent")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("tempest")
                                .then(Commands.literal("start").executes(ctx -> change(ctx.getSource().getLevel(), true, 1)))
                                .then(Commands.literal("stop").executes(ctx -> change(ctx.getSource().getLevel(), true, 0)))
                                .then(Commands.literal("natural").executes(ctx -> change(ctx.getSource().getLevel(), true, -1))))
                        .then(Commands.literal("whirlpool")
                                .then(Commands.literal("start").executes(ctx -> change(ctx.getSource().getLevel(), false, 1)))
                                .then(Commands.literal("stop").executes(ctx -> change(ctx.getSource().getLevel(), false, 0)))
                                .then(Commands.literal("natural").executes(ctx -> change(ctx.getSource().getLevel(), false, -1))))));
    }
    private static int change(ServerLevel level, boolean tempest, int action) {
        if (!level.dimension().equals(Asterion.LIMBO_LEVEL)) return 0;
        long start = action > 0 ? level.getGameTime() : action == 0 ? STOPPED : NATURAL;
        if (tempest) tempestStart = start; else whirlpoolStart = start;
        sync(level);
        level.players().forEach(player -> player.sendSystemMessage(Component.literal(
                (tempest ? "Limbo tempest" : "Limbo whirlpool") + (action > 0 ? " forming." : action == 0 ? " stopped." : " returned to its natural cycle."))));
        return 1;
    }
}
