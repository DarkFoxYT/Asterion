package net.krodark.asterion.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.krodark.asterion.Asterion;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Data-driven catalog for the visual biomes used inside the labyrinth. */
public final class MazeBiomes {
    private static final Identifier CATALOG = Asterion.id("maze_biomes.json");
    private static final Catalog FALLBACK = new Catalog(12, 7, 2.0F, 4.0F, List.of(
            new Biome(Kind.ANCIENT, 60, 0, 13, Set.of()),
            new Biome(Kind.OVERGROWTH, 40, 9, 3,
                    Set.of("mossy_walls", "leaf_crowns", "canopy", "floor_plants",
                            "moss_patches", "leaf_clusters", "bridges", "bridge_chains", "rest_sites",
                            "giant_dead_trees",
                            "ground_vines", "hanging_vines"))));
    private static volatile Catalog current = FALLBACK;
    private static volatile MinecraftServer loadedServer;

    private MazeBiomes() { }

    public static Catalog current() {
        return current;
    }

    public static synchronized void load(ServerLevel level) {
        if (loadedServer == level.getServer()) return;
        loadedServer = level.getServer();
        try {
            var resource = level.getServer().getResourceManager().getResource(CATALOG);
            if (resource.isEmpty()) {
                Asterion.LOGGER.warn("Missing {}; using built-in maze biomes", CATALOG);
                current = FALLBACK;
                return;
            }
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                int regionSize = positiveInt(root, "region_size_cells", 12);
                int centerRadius = nonNegativeInt(root, "ancient_center_radius_cells", 7);
                float transitionWidth = positiveFloat(root, "transition_width_cells", 1.5F);
                float blendWidth = nonNegativeFloat(root, "blend_width_cells", 2.0F);
                List<Biome> biomes = new ArrayList<>();
                JsonArray array = root.getAsJsonArray("biomes");
                if (array != null) for (var value : array) {
                    JsonObject object = value.getAsJsonObject();
                    Kind kind = parseKind(object.get("id").getAsString());
                    if (kind == null) {
                        Asterion.LOGGER.warn("Skipping unknown maze biome '{}'", object.get("id"));
                        continue;
                    }
                    Set<String> features = new HashSet<>();
                    JsonArray featureArray = object.getAsJsonArray("features");
                    if (featureArray != null) for (var feature : featureArray)
                        features.add(feature.getAsString().toLowerCase(Locale.ROOT));
                    biomes.add(new Biome(kind,
                            positiveInt(object, "weight", 1),
                            nonNegativeInt(object, "wall_opening_divisor", 0),
                            positiveInt(object, "motif_chance", kind == Kind.ANCIENT ? 13 : 3),
                            Set.copyOf(features)));
                }
                boolean hasAncient = biomes.stream().anyMatch(biome -> biome.kind == Kind.ANCIENT);
                if (biomes.isEmpty() || !hasAncient) {
                    Asterion.LOGGER.error("{} must contain at least the ancient biome; using built-in maze biomes", CATALOG);
                    current = FALLBACK;
                    return;
                }
                current = new Catalog(regionSize, centerRadius, transitionWidth, blendWidth, List.copyOf(biomes));
                Asterion.LOGGER.info("Loaded {} maze biomes from {}", biomes.size(), CATALOG);
            }
        } catch (Exception exception) {
            Asterion.LOGGER.error("Could not read {}; using built-in maze biomes", CATALOG, exception);
            current = FALLBACK;
        }
    }

    public static void reset() {
        current = FALLBACK;
        loadedServer = null;
    }

    private static Kind parseKind(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "ancient" -> Kind.ANCIENT;
            case "overgrowth" -> Kind.OVERGROWTH;
            default -> null;
        };
    }

    private static int positiveInt(JsonObject object, String key, int fallback) {
        return object.has(key) ? Math.max(1, object.get(key).getAsInt()) : fallback;
    }

    private static int nonNegativeInt(JsonObject object, String key, int fallback) {
        return object.has(key) ? Math.max(0, object.get(key).getAsInt()) : fallback;
    }

    private static float positiveFloat(JsonObject object, String key, float fallback) {
        return object.has(key) ? Math.max(0.01F, object.get(key).getAsFloat()) : fallback;
    }

    private static float nonNegativeFloat(JsonObject object, String key, float fallback) {
        return object.has(key) ? Math.max(0.0F, object.get(key).getAsFloat()) : fallback;
    }

    public enum Kind {
        ANCIENT,
        OVERGROWTH
    }

    public record Biome(Kind kind, int weight, int wallOpeningDivisor, int motifChance,
                        Set<String> features) {
        public boolean hasFeature(String feature) {
            return features.contains(feature);
        }
    }

    public record Catalog(int regionSizeCells, int ancientCenterRadiusCells,
                          float transitionWidthCells, float blendWidthCells,
                          List<Biome> biomes) {
        public Biome ancient() {
            return biomes.stream().filter(biome -> biome.kind == Kind.ANCIENT)
                    .findFirst().orElse(FALLBACK.biomes.getFirst());
        }

        public Biome select(long random) {
            int totalWeight = biomes.stream().mapToInt(Biome::weight).sum();
            int choice = (int)Math.floorMod(random, totalWeight);
            for (Biome biome : biomes) {
                choice -= biome.weight;
                if (choice < 0) return biome;
            }
            return ancient();
        }
    }
}
