package net.krodark.asterion.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.krodark.asterion.entity.CentipedeSegments;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public final class CentipedeCommands {
    private CentipedeCommands() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("centipede")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("segments")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(CentipedeSegments.MIN, CentipedeSegments.MAX))
                                                .executes(context -> {
                                                    int count = IntegerArgumentType.getInteger(context, "count");
                                                    int changed = 0;
                                                    for (var entity : EntityArgument.getEntities(context, "targets"))
                                                        if (entity instanceof ScarletCentipedeEntity centipede) {
                                                            centipede.setChainSegmentCount(count);
                                                            changed++;
                                                        }
                                                    if (changed == 0) {
                                                        context.getSource().sendFailure(Component.literal("No scarlet centipedes matched those targets."));
                                                        return 0;
                                                    }
                                                    int affected = changed;
                                                    context.getSource().sendSuccess(() -> Component.literal(
                                                            "Set " + affected + " scarlet centipede(s) to " + count + " segments."), true);
                                                    return changed;
                                                }))))));
    }
}
