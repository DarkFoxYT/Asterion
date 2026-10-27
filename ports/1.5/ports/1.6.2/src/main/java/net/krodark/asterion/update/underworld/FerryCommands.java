package net.krodark.asterion.update.underworld;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

/** Development control seat for tuning the ferry before its final model/interaction pass. */
public final class FerryCommands {
    private FerryCommands() { }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("asterion")
                        .then(Commands.literal("ferry")
                                .then(Commands.literal("ride")
                                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                        .executes(context -> {
                                            var player = context.getSource().getPlayerOrException();
                                            if (!player.level().dimension().equals(Asterion.LIMBO_LEVEL)) {
                                                context.getSource().sendFailure(Component.literal("The ferry can only be controlled in Limbo."));
                                                return 0;
                                            }
                                            var entity = player.level().getEntity(CharonsFerryEntity.SHARED_ID);
                                            if (!(entity instanceof CharonsFerryEntity ferry)) {
                                                context.getSource().sendFailure(Component.literal("Charon's ferry is not currently loaded."));
                                                return 0;
                                            }
                                            if (player.getVehicle() == ferry) {
                                                player.stopRiding();
                                                ferry.finishPlayerControl();
                                                context.getSource().sendSuccess(() -> Component.literal("Returned control to Charon."), true);
                                                return 1;
                                            }
                                            if (!ferry.beginPlayerControl(player)) {
                                                context.getSource().sendFailure(Component.literal("The ferry's control seat is occupied."));
                                                return 0;
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    "You are controlling Charon's ferry. Look to steer; move forward/back to sail."), true);
                                            return 1;
                                        })))));
    }
}
