package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.audio.MinotaurSoundPlayback;
import net.krodark.asterion.game.MinotaurSounds;
import net.krodark.asterion.network.MinotaurGlobalSoundPayload;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

public final class MinotaurSoundGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("execute in asterion:asterion_dimension run tp @a 800 150 800");
            context.waitTicks(20);
            context.runOnClient(client -> {
                for (int i = 0; i < 4; i++) {
                    var sound = new MinotaurSoundPlayback.GlobalRoar(MinotaurSounds.globalSound(i),
                            new MinotaurGlobalSoundPayload(i, 1, 1, 123));
                    check(sound.resolve(client.getSoundManager()) != null, "Global sound missing from resource manager");
                    check(sound.isRelative() && sound.getAttenuation() == SoundInstance.Attenuation.NONE,
                            "Roar still fades with distance");
                    check(sound.getSource() == SoundSource.HOSTILE && sound.getVolume() <= 1,
                            "Roar bypasses monster slider or uses excessive gain");
                }
                for (var sound : new net.minecraft.sounds.SoundEvent[]{Asterion.MINOTAUR_STEP,
                        Asterion.MINOTAUR_FIST_SWING, Asterion.MINOTAUR_FIST_SWING_COMBO,
                        Asterion.MINOTAUR_SWORD_SWING, Asterion.MINOTAUR_DOOR_BREAK}) {
                    var instance = new MinotaurSoundPlayback.GlobalRoar(sound,
                            new MinotaurGlobalSoundPayload(0, 1, 1, 123));
                    check(instance.resolve(client.getSoundManager()) != null, "Combat sound did not load");
                }
            });
            world.getServer().runOnServer(server -> {
                var level = server.getLevel(Asterion.ASTERION_LEVEL);
                check(!MinotaurSounds.playGlobal(level, Asterion.MINOTAUR_STEP, 1, 1), "Footsteps broadcast globally");
                check(MinotaurSounds.playGlobal(level, Asterion.MINOTAUR_ENTRY_ROAR, 6, 1), "Roar not broadcast");
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                try {
                    var manager = client.getSoundManager();
                    var field = java.util.Arrays.stream(manager.getClass().getDeclaredFields())
                            .filter(f -> f.getType().getSimpleName().equals("SoundEngine")).findFirst().orElseThrow();
                    field.setAccessible(true);
                    Object engine = field.get(manager);
                    boolean received = false;
                    for (var mapField : engine.getClass().getDeclaredFields()) {
                        if (!java.util.Map.class.isAssignableFrom(mapField.getType())) continue;
                        mapField.setAccessible(true);
                        Object value = mapField.get(engine);
                        if (value instanceof java.util.Map<?, ?> map)
                            received |= map.keySet().stream().anyMatch(key -> key instanceof MinotaurSoundPlayback.GlobalRoar);
                    }
                    check(received, "Distant player did not receive a playing global roar");
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            Asterion.LOGGER.info("PASS: loaded SFX, non-attenuated monster-channel roars, distant network playback and positional footstep routing");
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
