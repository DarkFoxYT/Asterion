package net.fabricmc.fabric.api.client.command.v2;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

@FunctionalInterface
public interface ClientCommandRegistrationCallback {
    Event<ClientCommandRegistrationCallback> EVENT =
            new Event<>(
                    callback ->
                            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                                    (net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
                                                    event) ->
                                            callback.register(
                                                    event.getDispatcher(),
                                                    event.getBuildContext())));

    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context);
}
