package net.krodark.asterion.command;

import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.krodark.asterion.WorldGenerator;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class PortalCommands {
    private static final double DISTANCE_AHEAD = 6.0D;

    private PortalCommands() { }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> dispatcher.register(
                Commands.literal("asterion")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("portal")
                                .executes(command -> summonInFront(command.getSource())))));
    }

    private static int summonInFront(net.minecraft.commands.CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal("The test portal can currently be summoned only in the Overworld."));
            return 0;
        }

        Vec3 look = player.getLookAngle();
        double horizontalLength = Math.sqrt(look.x * look.x + look.z * look.z);
        double forwardX = horizontalLength < 1.0E-4D ? 0.0D : look.x / horizontalLength;
        double forwardZ = horizontalLength < 1.0E-4D ? 1.0D : look.z / horizontalLength;
        int x = net.minecraft.util.Mth.floor(player.getX() + forwardX * DISTANCE_AHEAD);
        int z = net.minecraft.util.Mth.floor(player.getZ() + forwardZ * DISTANCE_AHEAD);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos center = new BlockPos(x, surfaceY, z);

        WorldGenerator.summonPortal(level, center, surfaceY);
        source.sendSuccess(() -> Component.literal(
                "Tore open a Asterion portal at " + x + ", " + surfaceY + ", " + z + "."), true);
        return Command.SINGLE_SUCCESS;
    }
}
