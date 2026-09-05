package net.krodark.asterion.dev.verification;

import com.google.gson.Gson;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.BiomeMusic;
import net.minecraft.client.Minecraft;
import java.util.Arrays;
import java.util.Comparator;

final class EndingMusicCheck {
    static void run(Minecraft client) {
        try (var reader = client.getResourceManager().getResourceOrThrow(Asterion.id("music_tracks.json")).openAsReader()) {
            var tracks = new Gson().fromJson(reader, BiomeMusic.Track[].class);
            var compatible = BiomeMusic.class.getDeclaredMethod("compatible", BiomeMusic.Track.class, String.class);
            compatible.setAccessible(true);
            var ending = new java.util.ArrayList<BiomeMusic.Track>();
            for (var track : tracks) if ((boolean)compatible.invoke(null, track, "victory")) ending.add(track);
            ending.sort(Comparator.comparing(BiomeMusic.Track::title));
            if (ending.size() != 2
                    || !ending.get(0).title().equals("if we could roll back the credits, one last time")
                    || !ending.get(1).title().equals("ill see you, at the edge of the world"))
                throw new AssertionError("Wrong ending playlist: " + ending);
            if (Arrays.stream(tracks).noneMatch(t -> t.group().equals("arena")))
                throw new AssertionError("Combat playlist disappeared");
            Asterion.LOGGER.info("PASS: bundled victory playlist contains only Credits then Edge of the World");
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
