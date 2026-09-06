package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

 
public final class MazeAmbience {
    private static Loop maze, cave;
    private static int ticks;
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private MazeAmbience() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(MazeAmbience::tick);
    }

    private static void tick(Minecraft client) {
        if (level != client.level || client.level == null || client.player == null) {
            if (maze != null) client.getSoundManager().stop(maze);
            if (cave != null) client.getSoundManager().stop(cave);
            maze = cave = null;
            level = client.level;
            if (level == null || client.player == null) return;
        }
        if (client.isPaused()) return;
        ticks++;
        boolean active = client.level.dimension().equals(Asterion.ASTERION_LEVEL)
                && client.player.isAlive()
                && client.options.getSoundSourceVolume(SoundSource.AMBIENT) > 0
                && client.options.getSoundSourceVolume(SoundSource.MASTER) > 0;
        boolean underground = client.player.getY()
                < net.krodark.asterion.worldgen.LabyrinthLevels.CAVE_ROOF_Y;
        maze = update(client, maze, "maze_ambience", active && !underground);
        cave = update(client, cave, "cave_ambience", active && underground);
    }

    private static Loop update(Minecraft client, Loop loop, String sound, boolean active) {
        if (loop != null) {
            loop.target = active ? .10F : 0;
            if (ticks - loop.started > 40 && !client.getSoundManager().isActive(loop)) loop = null;
        }
        if (loop == null && active) {
            loop = new Loop(sound);
            client.getSoundManager().play(loop);
        }
        return loop;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final int started = ticks;
        private float target = .10F;
        private Loop(String sound) {
            super(SoundEvent.createVariableRangeEvent(Asterion.id(sound)),
                    SoundSource.AMBIENT, RandomSource.create());
            relative = true;
            attenuation = Attenuation.NONE;
            looping = true;
            delay = 0;  
            volume = .001F;
        }
        @Override public void tick() {
            volume += Math.clamp(target - volume, -.0025F, .0025F);
            if (target == 0 && volume <= .001F) stop();
        }
    }
}
