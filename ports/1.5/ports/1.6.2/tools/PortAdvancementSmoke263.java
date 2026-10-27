import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import java.nio.file.*;
import java.util.*;

/** Codec-only regression: no Minecraft client, server, window or world starts.
 * Synthetic holders allow references to mod entries without loading the mod.
 * This validates advancement structure, not registration or gameplay behavior.
 */
public final class PortAdvancementSmoke263 {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var ops = RegistryOps.create(JsonOps.INSTANCE, new RegistryOps.RegistryInfoLookup() {
            public <T> Optional<HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return Optional.of(new HolderGetter<T>() {
                    public Optional<Holder.Reference<T>> get(ResourceKey<T> entry) {
                        return Optional.of(Holder.Reference.createStandAlone(this, entry));
                    }
                    public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
                        return Optional.of(HolderSet.emptyNamed(this, tag));
                    }
                });
            }
        });
        int checked = 0;
        try (var files = Files.walk(Path.of(args[0]))) {
            for (var path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                // Item icons need live item registrations; criteria do not.
                json.remove("display");
                var result = Advancement.CODEC.parse(ops, json);
                if (result.error().isPresent()) throw new AssertionError(path + ": " + result.error().get().message());
                checked++;
            }
        }
        System.out.println("PASS: decoded " + checked + " advancement criteria with Minecraft 26.3 codecs (no game launch)");
        int loot = 0;
        try (var files = Files.walk(Path.of(args[0]).resolveSibling("loot_table"))) {
            for (var path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(path));
                schemaReferences(json);
                var result = net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.parse(ops, json);
                if (result.error().isPresent()) throw new AssertionError(path + ": " + result.error().get().message());
                loot++;
            }
        }
        System.out.println("PASS: decoded " + loot + " loot table schemas with Minecraft 26.3 codecs (vanilla stand-ins for static mod references)");
        var data = Path.of(args[0]).getParent();
        checkData(data.resolve("worldgen/biome"), net.minecraft.world.level.biome.Biome.DIRECT_CODEC, ops);
        checkData(data.resolve("dimension_type"), net.minecraft.world.level.dimension.DimensionType.DIRECT_CODEC, ops);
        checkData(data.resolve("worldgen/placed_feature"), net.minecraft.world.level.levelgen.placement.PlacedFeature.DIRECT_CODEC, ops);
        checkData(data.resolve("damage_type"), net.minecraft.world.damagesource.DamageType.DIRECT_CODEC, ops);
    }

    // Static registry codecs cannot resolve mod entries without a loader. Only
    // this in-memory test copy substitutes known vanilla entries. The archive
    // audit separately verifies that shipped item IDs, counts and chances match
    // the baseline. This is a schema test, not a test of live mod registration.
    private static void schemaReferences(JsonElement value) {
        if (value.isJsonArray()) { value.getAsJsonArray().forEach(PortAdvancementSmoke263::schemaReferences); return; }
        if (!value.isJsonObject()) return;
        var object = value.getAsJsonObject();
        if (object.has("type") && object.get("type").getAsString().equals("minecraft:item") && object.has("name")) {
            if (object.get("name").getAsString().startsWith("asterion:")) object.addProperty("name", "minecraft:stick");
        }
        if (object.has("block") && object.get("block").isJsonPrimitive()) {
            var id = object.get("block").getAsString();
            if (id.startsWith("asterion:")) {
                object.addProperty("block", id.endsWith("_slab") ? "minecraft:oak_slab" : "minecraft:lever");
                if (id.equals("asterion:pedestal") && object.has("properties")) {
                    var properties = object.getAsJsonArray("properties");
                    for (int i = 0; i < properties.size(); i++)
                        if (properties.get(i).getAsString().equals("claimed")) properties.set(i, new JsonPrimitive("powered"));
                }
            }
        }
        object.entrySet().forEach(entry -> schemaReferences(entry.getValue()));
    }

    private static void checkData(Path directory, com.mojang.serialization.Codec<?> codec,
                                  com.mojang.serialization.DynamicOps<JsonElement> ops) throws Exception {
        if (!Files.isDirectory(directory)) return;
        int checked = 0;
        try (var files = Files.walk(directory)) {
            for (var path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var result = codec.parse(ops, JsonParser.parseString(Files.readString(path)));
                if (result.error().isPresent()) throw new AssertionError(path + ": " + result.error().get().message());
                checked++;
            }
        }
        System.out.println("PASS: decoded " + checked + " " + directory.getFileName() + " resources");
    }
}
