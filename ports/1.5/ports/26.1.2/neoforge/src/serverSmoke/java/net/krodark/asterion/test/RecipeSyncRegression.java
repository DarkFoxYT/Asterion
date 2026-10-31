package net.krodark.asterion.test;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.krodark.asterion.Asterion;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class RecipeSyncRegression {
    private RecipeSyncRegression() {}

    public static void run(RegistryAccess registries) {
        roundTrip(Asterion.FORGED_SWORD_RECIPE, registries, "forged_sword");
        roundTrip(Asterion.REMOVED_RECIPE, registries, "removed");
        if (Boolean.getBoolean("asterion.recipeReproduce")) Asterion.LOGGER.info("ASTERION_RECIPE_SYNC REPRODUCED: both original unit codecs rejected JSON-loaded recipes");
        else Asterion.LOGGER.info("ASTERION_RECIPE_SYNC PASS: JSON-loaded recipes encoded, decoded and re-encoded without payload data");
    }

    private static <T extends Recipe<?>> void roundTrip(RecipeSerializer<T> serializer, RegistryAccess registries, String name) {
        T recipe = serializer.codec().codec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            try {
                serializer.streamCodec().encode(buffer, recipe);
            } catch (RuntimeException error) {
                if (Boolean.getBoolean("asterion.recipeReproduce") && error.getMessage().contains("expected")) {
                    Asterion.LOGGER.info("ASTERION_RECIPE_SYNC REPRODUCED {}: {}", name, error.getMessage());
                    return;
                }
                throw error;
            }
            if (Boolean.getBoolean("asterion.recipeReproduce")) throw new AssertionError("Original failure did not reproduce: " + name);
            if (buffer.readableBytes() != 0) throw new AssertionError("Stateless recipe added network data: " + name);
            T decoded = serializer.streamCodec().decode(buffer);
            if (decoded.getClass() != recipe.getClass() || decoded.getSerializer() != serializer)
                throw new AssertionError("Wrong recipe after network decode: " + name);
            serializer.streamCodec().encode(buffer, decoded);
            serializer.codec().codec().encodeStart(JsonOps.INSTANCE, decoded).getOrThrow();
        } finally {
            buffer.release();
        }
    }
}
