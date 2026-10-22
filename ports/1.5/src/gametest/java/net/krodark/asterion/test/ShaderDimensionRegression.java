package net.krodark.asterion.test;

import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;

final class ShaderDimensionRegression {
    private static int fullScreenPasses;
    static void register() {
        for (var stage : com.meekdev.amnetic.client.pipeline.RenderStage.values()) {
            if (stage == com.meekdev.amnetic.client.pipeline.RenderStage.AFTER_HAND
                    || stage == com.meekdev.amnetic.client.pipeline.RenderStage.BEFORE_GUI
                    || stage == com.meekdev.amnetic.client.pipeline.RenderStage.AFTER_GUI) continue;
            com.meekdev.amnetic.client.pipeline.Pipeline.add(stage, -99999, "Shader isolation regression",
                    context -> fullScreenPasses++);
        }
    }

    static void checkPasses(boolean maze) {
        if (maze ? fullScreenPasses == 0 : fullScreenPasses != 0)
            throw new AssertionError("Full-screen Amnetic passes did not respect shader dimension isolation");
        if (net.krodark.asterion.port.client.ShaderCompatibility.externalShadersActive() == maze)
            throw new AssertionError("External shader detection disagrees with the active dimension");
    }

    static void leaveLabyrinth(Minecraft client) {
        client.getSingleplayerServer().execute(() -> {
            var server = client.getSingleplayerServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamemode spectator @a");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "execute in minecraft:overworld run tp @a 0 120 0");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
        });
        int[] ticks = {0}, externalPasses = {-1};
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (++ticks[0] > 200) throw new AssertionError("Shader pack did not resume after leaving the maze");
            if (ticks[0] < 40 || c.level == null || !c.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                if (!(Boolean) api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null)))
                    throw new AssertionError("The selected pack did not resume in the overworld");
                if (externalPasses[0] < 0) {
                    externalPasses[0] = fullScreenPasses;
                    c.options.hideGui = true;
                    c.player.setXRot(15); c.player.xRotO = 15;
                    return;
                }
                if (fullScreenPasses != externalPasses[0]) throw new AssertionError("Amnetic modified external shader lighting after leaving the maze");
                if (ticks[0] < 70) return;
                c.player.setXRot(15); c.player.xRotO = 15;
                net.minecraft.client.Screenshot.grab(c.gameDirectory, "asterion-shader-overworld.png",
                        c.getMainRenderTarget(), message -> {});
                Asterion.LOGGER.info("ASTERION_SHADER_DIMENSION PASS: external pack disabled in maze and resumed in overworld");
                c.stop();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }
}
