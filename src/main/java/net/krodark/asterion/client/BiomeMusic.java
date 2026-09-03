package net.krodark.asterion.client;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.client.render.portal.AsterionPortalRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.util.*;

/** One streamed music voice, server-selected biome, brief credits and quiet per-region gains. */
public final class BiomeMusic {
    public record Track(String group, String sound, String title, String artist) { }
    private static ClientLevel level;
    private static int biome = -1, ticks, notice, gap;
    private static boolean arena, defeatedBossNearby;
    private static String lastGroup = "";
    private static Voice voice;
    private static Track playing, previous;
    private static List<Track> tracks = List.of();
    private BiomeMusic() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(BiomeMusic::tick);
        HudElementRegistry.addLast(Asterion.id("now_playing"), (graphics, delta) -> {
            var client = Minecraft.getInstance();
            if (notice <= 0 || playing == null || client.options.hideGui || !ownsMusic()) return;
            float fade = Math.min(1F, Math.min((120 - notice) / 12F, notice / 20F));
            int alpha = Math.max(4, (int)(fade * 255));
            int center = graphics.guiWidth() / 2;
            String title = client.font.plainSubstrByWidth("Now playing: " + playing.title(), graphics.guiWidth() - 24);
            String artist = client.font.plainSubstrByWidth("by " + playing.artist(), graphics.guiWidth() - 24);
            int y = arena ? 60 : 8; // Leave the health and rage bars unobstructed.
            graphics.centeredText(client.font, Component.literal(title), center, y, alpha << 24 | 0xE9E6D9);
            graphics.centeredText(client.font, Component.literal(artist), center, y + 11, alpha << 24 | 0xADB9B5);
        });
    }

    public static void setBiome(int value) {
        var client = Minecraft.getInstance();
        if (level != client.level) reset(client);
        level = client.level;
        biome = value;
    }

    public static boolean ownsMusic() {
        var client = Minecraft.getInstance();
        return client.level != null && client.level.dimension().equals(Asterion.ASTERION_LEVEL);
    }

    public static String group(int biome, boolean arena) {
        return arena ? "arena" : switch (biome) {
            case 0 -> "ancient"; case 1 -> "overgrown"; case 2 -> "crimsonmarshlands"; default -> "";
        };
    }

    public static float gain(String group) {
        return switch (group) { case "ancient" -> .12F; case "arena" -> .55F; default -> .32F; };
    }

    private static void tick(Minecraft client) {
        if (level != client.level) { reset(client); level = client.level; }
        if (!ownsMusic() || client.player == null || !client.player.isAlive()) { stop(client); return; }
        if (client.isPaused()) return;
        if (notice > 0) notice--;
        if (++ticks % 10 == 0) {
            var nearbyBosses = level.getEntitiesOfClass(MinotaurEntity.class,
                    client.player.getBoundingBox().inflate(128),
                    boss -> boss.behaviorPhase() == MinotaurEntity.BehaviorPhase.BOSS);
            boolean activeBossNearby = nearbyBosses.stream()
                    .anyMatch(boss -> boss.isAlive() && !boss.isDefeatedBoss());
            // A permanent death-pose Minotaur intentionally remains as an entity. It must not
            // silence a living/revived boss that is also tracked by the client.
            defeatedBossNearby = !activeBossNearby
                    && nearbyBosses.stream().anyMatch(MinotaurEntity::isDefeatedBoss);
            arena = WorldGenerator.isInsideBossArena(client.player.position()) && activeBossNearby;
        }
        // The permanent corpse intentionally retains one health point, so isAlive()
        // alone cannot distinguish victory from an active encounter.
        if (defeatedBossNearby) { stop(client); return; }
        boolean victory = !arena && WorldGenerator.isInsideBossArena(client.player.position())
                && AsterionPortalRenderer.isOpen();
        String desired = victory ? "victory" : group(biome, arena);
        if (!desired.equals(lastGroup)) { gap = 0; lastGroup = desired; }
        float volume = AsterionConfig.INSTANCE.musicVolumePercent / 100F;
        boolean audible = volume > 0 && client.options.getSoundSourceVolume(SoundSource.MUSIC) > 0
                && client.options.getSoundSourceVolume(SoundSource.MASTER) > 0;
        if (voice != null) {
            voice.target = audible && compatible(playing,desired) ? gain(desired) * volume : 0;
            if (!client.getSoundManager().isActive(voice) && ticks - voice.started > 40) {
                boolean changed = !compatible(playing,desired);
                previous = playing; voice = null; playing = null; notice = 0;
                gap = changed ? 0 : 100 + level.getRandom().nextInt(201);
            }
            return;
        }
        if (!audible || desired.isEmpty()) return;
        if (gap > 0) { gap--; return; }
        if (tracks.isEmpty()) loadTracks(client);
        var choices = tracks.stream().filter(t -> desired.equals("victory")
                ? !t.group().equals("arena") && !t.group().equals("crypts")
                : t.group().equals(desired)).toList();
        if (choices.isEmpty()) { gap = 200; return; }
        var candidates = choices.size() > 1 ? choices.stream().filter(t -> !t.equals(previous)).toList() : choices;
        playing = candidates.get(level.getRandom().nextInt(candidates.size()));
        voice = new Voice(playing, ticks, gain(desired) * volume);
        client.getSoundManager().play(voice);
        notice = 120;
    }
    private static boolean compatible(Track track,String desired) {
        return desired.equals("victory") ? !track.group().equals("arena") && !track.group().equals("crypts")
                : track.group().equals(desired);
    }

    private static void loadTracks(Minecraft client) {
        try (var reader = client.getResourceManager().getResourceOrThrow(Asterion.id("music_tracks.json")).openAsReader()) {
            tracks = List.of(new Gson().fromJson(reader, Track[].class));
        } catch (Exception error) { Asterion.LOGGER.warn("Could not load biome music playlist", error); }
    }

    private static void stop(Minecraft client) {
        if (voice != null) client.getSoundManager().stop(voice);
        voice = null; playing = null; notice = 0; arena = false; gap = 0;
    }
    private static void reset(Minecraft client) {
        stop(client); biome = -1; tracks = List.of(); previous = null; lastGroup = ""; defeatedBossNearby = false;
    }

    private static final class Voice extends AbstractTickableSoundInstance {
        private final int started;
        private float target;
        private Voice(Track track, int started, float target) {
            super(SoundEvent.createVariableRangeEvent(Identifier.parse(track.sound())), SoundSource.MUSIC, RandomSource.create());
            this.started = started; this.target = target;
            volume = .001F; relative = true; attenuation = Attenuation.NONE;
        }
        @Override public void tick() {
            volume += Math.clamp(target - volume, -.012F, .004F);
            if (target == 0 && volume <= .001F) stop();
        }
    }
}
