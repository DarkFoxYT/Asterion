package net.krodark.asterion.event;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.CatacombLayout;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Shared, persistent flood-event signal. Water movement and event timing remain the controller's job. */
public final class CatacombFloodState extends SavedData {
    private static final SavedDataType<CatacombFloodState> TYPE = new SavedDataType<>(
            Asterion.id("catacomb_flood"), CatacombFloodState::new,
            Codec.BOOL.fieldOf("active").codec().xmap(CatacombFloodState::new, state -> state.active), null);
    private boolean active;
    private CatacombFloodState() { }
    private CatacombFloodState(boolean active) { this.active = active; }

    public static boolean isFlooding(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) return false;
        boolean catacombs = pos.getY() >= 3 && pos.getY() <= CatacombLayout.ROOF_Y;
        boolean arena = pos.getY() >= 36 && pos.getY() <= 61
                && Math.abs((long)pos.getX()) <= 34 && Math.abs((long)pos.getZ()) <= 34;
        return (catacombs || arena) && level.getDataStorage().computeIfAbsent(TYPE).active;
    }

    public static void setActive(ServerLevel level, boolean active) {
        var state = level.getDataStorage().computeIfAbsent(TYPE);
        if (state.active != active) { state.active = active; state.setDirty(); }
    }

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            var flood = Commands.literal("flooding");
            for (boolean active : new boolean[]{true, false})
                flood.then(Commands.literal(active ? "start" : "stop").executes(command -> {
                    ServerLevel maze = command.getSource().getServer().getLevel(Asterion.ASTERION_LEVEL);
                    if (maze == null) return 0;
                    setActive(maze, active);
                    command.getSource().sendSuccess(() -> Component.literal("Catacomb flood signal "
                            + (active ? "active" : "inactive") + "; this command does not move water."), true);
                    return 1;
                }));
            dispatcher.register(Commands.literal("asterion")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.literal("catacombs").then(flood)));
        });
    }
}
