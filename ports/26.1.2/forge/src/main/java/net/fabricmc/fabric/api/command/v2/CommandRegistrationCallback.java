package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Forge command registration adapter for shared commands. */
public final class CommandRegistrationCallback {
    public static final Event EVENT = new Event();
    private CommandRegistrationCallback() {}

    @FunctionalInterface public interface Callback {
        void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context,
                      Commands.CommandSelection environment);
    }

    public static final class Event {
        public void register(Callback callback) {
            RegisterCommandsEvent.BUS.addListener(event -> callback.register(
                    event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
        }
    }
}
