package net.fabricmc.fabric.api.client.command.v2;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;

public final class ClientCommands {
    private ClientCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(
            String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }
}
