package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.concurrent.CopyOnWriteArrayList;

@FunctionalInterface
public interface CommandRegistrationCallback {
    CopyOnWriteArrayList<CommandRegistrationCallback> LISTENERS = new CopyOnWriteArrayList<>();
    Event<CommandRegistrationCallback> EVENT = LISTENERS::add;

    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context,
                  Commands.CommandSelection environment);

    static void fire(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context,
                     Commands.CommandSelection environment) {
        LISTENERS.forEach(listener -> listener.register(dispatcher, context, environment));
    }
}
