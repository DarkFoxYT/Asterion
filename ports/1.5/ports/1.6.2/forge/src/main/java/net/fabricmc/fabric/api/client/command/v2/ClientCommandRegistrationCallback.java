package net.fabricmc.fabric.api.client.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

@FunctionalInterface
public interface ClientCommandRegistrationCallback {
    Registration EVENT = new Registration();
    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context);
    final class Registration {
        public void register(ClientCommandRegistrationCallback callback) {
            net.minecraftforge.client.event.RegisterClientCommandsEvent.BUS.addListener(event ->
                    callback.register(event.getDispatcher(), event.getBuildContext()));
        }
    }
}
