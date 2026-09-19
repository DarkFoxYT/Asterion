package net.krodark.asterion.port.client;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.game.MinotaurSounds;
import net.krodark.asterion.network.MinotaurGlobalSoundPayload;
import net.krodark.asterion.worldgen.WorldGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.Comparator;
import java.util.List;

/** Dimension music, now-playing HUD, ambience loops and global boss audio. */
public final class PortAudio {
    public record Track(String group, String sound, String title, String artist) {}

    private static ClientLevel level;
    private static int biome = -1, ticks, notice, gap;
    private static boolean credits;
    private static boolean arena, defeatedBossNearby;
    private static int victoryTrack;
    private static String lastGroup = "";
    private static Track playing, previous;
    private static List<Track> tracks = List.of();
    private static MusicVoice music;
    private static AmbientLoop mazeAmbience, caveAmbience;

    private PortAudio() {}

    public static void initialize() {
        HudRenderCallback.EVENT.register((graphics, tracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (notice <= 0 || playing == null || client.options.hideGui || !ownsMusic()) return;
            float fade = Math.min(1.0F, Math.min((120 - notice) / 12.0F, notice / 20.0F));
            int alpha = Math.max(4, (int)(fade * 255.0F));
            int center = graphics.guiWidth() / 2;
            String title = client.font.plainSubstrByWidth("Now playing: " + playing.title(), graphics.guiWidth() - 24);
            String artist = client.font.plainSubstrByWidth("by " + playing.artist(), graphics.guiWidth() - 24);
            int y = arena ? 60 : 8;
            graphics.drawCenteredString(client.font, Component.literal(title), center, y, alpha << 24 | 0xE9E6D9);
            graphics.drawCenteredString(client.font, Component.literal(artist), center, y + 11, alpha << 24 | 0xADB9B5);
        });
    }

    public static boolean ownsMusic() {
        Minecraft client = Minecraft.getInstance();
        return credits || client.level != null && client.level.dimension().equals(Asterion.ASTERION_LEVEL);
    }

    public static void setBiome(int value) {
        biome = net.krodark.asterion.port.compat.MathCompat.clamp(value, 0, 4);
    }

    public static void beginCredits() { credits = true; gap = 0; }
    public static void endCredits() { credits = false; stopAll(Minecraft.getInstance()); }
    public static void tick(Minecraft client) {
        if (credits) {
            if (client.level == null || client.player == null) { endCredits(); return; }
            level = client.level;
            if (!client.isPaused()) { ticks++; if(notice > 0) notice--; tickMusic(client, "victory"); }
            return;
        }

        if (level != client.level) {
            stopAll(client);
            level = client.level;
        }
        if (client.level == null || client.player == null || !ownsMusic() || !client.player.isAlive()) {
            stopAll(client);
            return;
        }
        if (client.isPaused()) return;
        ticks++;
        if (notice > 0) notice--;
        boolean cave = client.player.getY() <= net.krodark.asterion.worldgen.LabyrinthLevels.CAVE_ROOF_Y;
        if (ticks % 10 == 0) {
            var nearbyBosses = client.level.getEntitiesOfClass(net.krodark.asterion.entity.MinotaurEntity.class,
                    client.player.getBoundingBox().inflate(128.0D),
                    boss -> boss.behaviorPhase() == net.krodark.asterion.entity.MinotaurEntity.BehaviorPhase.BOSS);
            boolean activeBoss = nearbyBosses.stream().anyMatch(boss -> boss.isAlive() && !boss.isDefeatedBoss());
            defeatedBossNearby = !activeBoss && nearbyBosses.stream()
                    .anyMatch(net.krodark.asterion.entity.MinotaurEntity::isDefeatedBoss);
            arena = WorldGenerator.isInsideBossArena(client.player.position()) && activeBoss;
            if (activeBoss) victoryTrack = 0;
        }
        boolean victory = !arena && WorldGenerator.isInsideBossArena(client.player.position())
                && (defeatedBossNearby || PortPortalRenderer.isOpen());
        // Keep music alive throughout the dimension. Caves use the restrained
        // ancient score while an active Minotaur promotes the authored arena set.
        String desired = victory ? "victory" : arena ? "arena" : cave ? "ancient" : group(biome);
        if (!desired.equals(lastGroup)) {
            if (victory && music != null) client.getSoundManager().stop(music);
            gap = 0;
            lastGroup = desired;
        }
        tickMusic(client, desired);
        mazeAmbience = updateAmbience(client, mazeAmbience, "maze_ambience", !cave);
        caveAmbience = updateAmbience(client, caveAmbience, "cave_ambience", cave);
    }

    private static String group(int value) {
        return switch (value) {
            case 1 -> "overgrown";
            case 2 -> "crimsonmarshlands";
            case 4 -> "forge";
            default -> "ancient";
        };
    }

    private static float gain(String group) {
        return switch (group) { case "ancient" -> 0.20F; case "forge" -> 0.28F; case "arena" -> .55F; default -> 0.34F; };
    }

    private static void tickMusic(Minecraft client, String desired) {
        float configured = AsterionConfig.INSTANCE.musicVolumePercent / 100.0F;
        boolean audible = configured > 0 && client.options.getSoundSourceVolume(SoundSource.MUSIC) > 0
                && client.options.getSoundSourceVolume(SoundSource.MASTER) > 0;
        if (music != null) {
            music.target = audible && compatible(playing, desired)
                    ? gain(desired) * configured : 0.0F;
            if (!client.getSoundManager().isActive(music) && ticks - music.started > 40) {
                boolean changed = !compatible(playing, desired);
                previous = playing;
                music = null;
                playing = null;
                notice = 0;
                gap = changed ? 0 : desired.equals("victory") ? 40 : 100 + client.level.random.nextInt(201);
            }
            return;
        }
        if (!audible || desired.isEmpty()) return;
        if (gap > 0) { gap--; return; }
        if (tracks.isEmpty()) loadTracks(client);
        List<Track> choices = tracks.stream().filter(track -> compatible(track, desired))
                .sorted(Comparator.comparing(Track::title)).toList();
        if (choices.isEmpty()) { gap = 200; return; }
        if (desired.equals("victory")) {
            if (victoryTrack >= choices.size()) return;
            playing = choices.get(victoryTrack++);
        } else {
            List<Track> candidates = choices.size() > 1
                    ? choices.stream().filter(track -> !track.equals(previous)).toList() : choices;
            playing = candidates.get(client.level.random.nextInt(candidates.size()));
        }
        music = new MusicVoice(playing, gain(desired) * configured);
        client.getSoundManager().play(music);
        notice = 120;
    }

    private static boolean compatible(Track track, String desired) {
        if (track == null) return false;
        return desired.equals("victory")
                ? track.title().equals("if we could roll back the credits, one last time")
                        || track.title().equals("ill see you, at the edge of the world")
                : track.group().equals(desired);
    }

    private static void loadTracks(Minecraft client) {
        try (var reader = client.getResourceManager().getResourceOrThrow(Asterion.id("music_tracks.json")).openAsReader()) {
            tracks = List.of(new Gson().fromJson(reader, Track[].class));
        } catch (Exception error) {
            Asterion.LOGGER.warn("Could not load Asterion dimension music", error);
        }
    }

    private static AmbientLoop updateAmbience(Minecraft client, AmbientLoop loop, String sound, boolean wanted) {
        boolean audible = wanted && client.options.getSoundSourceVolume(SoundSource.AMBIENT) > 0
                && client.options.getSoundSourceVolume(SoundSource.MASTER) > 0;
        if (loop != null) {
            loop.target = audible ? 0.10F : 0.0F;
            if (ticks - loop.started > 40 && !client.getSoundManager().isActive(loop)) loop = null;
        }
        if (loop == null && audible) {
            loop = new AmbientLoop(sound);
            client.getSoundManager().play(loop);
        }
        return loop;
    }

    private static void stopAll(Minecraft client) {
        if (music != null) client.getSoundManager().stop(music);
        if (mazeAmbience != null) client.getSoundManager().stop(mazeAmbience);
        if (caveAmbience != null) client.getSoundManager().stop(caveAmbience);
        music = null;
        playing = null;
        mazeAmbience = caveAmbience = null;
        notice = gap = 0;
        lastGroup = "";
        arena = defeatedBossNearby = false;
        victoryTrack = 0;
    }

    public static void playGlobal(MinotaurGlobalSoundPayload payload) {
        SoundEvent event = MinotaurSounds.globalSound(payload.sound());
        if (event == null || !Float.isFinite(payload.volume()) || !Float.isFinite(payload.pitch())) return;
        Minecraft.getInstance().getSoundManager().play(new GlobalSound(event, payload));
    }

    private static final class MusicVoice extends AbstractTickableSoundInstance {
        private final int started = ticks;
        private float target;
        private MusicVoice(Track track, float target) {
            super(SoundEvent.createVariableRangeEvent(ResourceLocation.parse(track.sound())),
                    SoundSource.MUSIC, RandomSource.create());
            this.target = target;
            volume = 0.001F;
            relative = true;
            attenuation = Attenuation.NONE;
        }
        @Override public void tick() {
            volume += net.krodark.asterion.port.compat.MathCompat.clamp(target - volume, -0.012F, 0.004F);
            if (target == 0 && volume <= 0.001F) stop();
        }
    }

    private static final class AmbientLoop extends AbstractTickableSoundInstance {
        private final int started = ticks;
        private float target = 0.10F;
        private AmbientLoop(String sound) {
            super(SoundEvent.createVariableRangeEvent(Asterion.id(sound)), SoundSource.AMBIENT, RandomSource.create());
            relative = true;
            attenuation = Attenuation.NONE;
            looping = true;
            delay = 0;
            volume = 0.001F;
        }
        @Override public void tick() {
            volume += net.krodark.asterion.port.compat.MathCompat.clamp(target - volume, -0.0025F, 0.0025F);
            if (target == 0 && volume <= 0.001F) stop();
        }
    }

    private static final class GlobalSound extends AbstractSoundInstance {
        private GlobalSound(SoundEvent event, MinotaurGlobalSoundPayload payload) {
            super(event, SoundSource.HOSTILE, RandomSource.create(payload.seed()));
            volume = net.krodark.asterion.port.compat.MathCompat.clamp(payload.volume(), 0.0F, 1.0F);
            pitch = net.krodark.asterion.port.compat.MathCompat.clamp(payload.pitch(), 0.5F, 2.0F);
            relative = true;
            attenuation = Attenuation.NONE;
        }
    }
}
