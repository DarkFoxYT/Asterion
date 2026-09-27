package net.krodark.asterion.forge;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.krodark.asterion.client.cinematic.studio.CutsceneStudio;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ForgeCutsceneCommands {
    private ForgeCutsceneCommands() {}

    public static void initialize() {
        RegisterClientCommandsEvent.BUS.addListener(event -> event.getDispatcher().register(
                literal("cutscene")
                        .then(literal("play").then(argument("file", StringArgumentType.word()).executes(context -> {
                            try {
                                CutsceneStudio.play(StringArgumentType.getString(context, "file"));
                                context.getSource().sendSuccess(() -> Component.literal("Cutscene playing."), false);
                                return 1;
                            } catch (Exception error) {
                                context.getSource().sendFailure(Component.literal("Cutscene: " + error.getMessage()));
                                return 0;
                            }
                        })))
                        .then(literal("stop").executes(context -> {
                            CutsceneStudio.stop();
                            return 1;
                        }))));
    }
}
