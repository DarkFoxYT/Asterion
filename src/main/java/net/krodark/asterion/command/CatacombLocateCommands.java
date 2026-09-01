package net.krodark.asterion.command;

import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.AuthoredCatacombs;
import net.krodark.asterion.worldgen.ZoneRunePlacement;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;

/** Locates the deterministic authored rooms that do not live in the structure registry. */
public final class CatacombLocateCommands {
    private CatacombLocateCommands() { }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            // Extend vanilla's locate root with the authored room name players expect.
            dispatcher.register(Commands.literal("locate")
                    .then(Commands.literal("catacomb_brazier_room")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .executes(command -> locateBrazierRoom(command.getSource()))));
            // Keep the same locator available under the mod's command namespace too.
            dispatcher.register(Commands.literal("asterion")
                    .then(Commands.literal("locate")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .then(Commands.literal("brazier_room")
                                    .executes(command -> locateBrazierRoom(command.getSource())))));
        });
    }

    private static int locateBrazierRoom(CommandSourceStack source) {
        ServerLevel level = source.getServer().getLevel(Asterion.ASTERION_LEVEL);
        if (level == null) {
            source.sendFailure(Component.literal("The Asterion dimension is not available."));
            return 0;
        }

        ZoneRunePlacement.enqueueCursedBrazierRoom(level);
        BlockPos target = AuthoredCatacombs.BRAZIER_ROOM_ORIGIN.offset(25, 5, 25);
        String coordinates = target.getX() + " " + target.getY() + " " + target.getZ();
        source.sendSuccess(() -> Component.literal("Catacomb brazier room: ")
                .append(Component.literal("[" + coordinates + "]").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" in asterion:asterion_dimension")), false);
        return Command.SINGLE_SUCCESS;
    }
}
