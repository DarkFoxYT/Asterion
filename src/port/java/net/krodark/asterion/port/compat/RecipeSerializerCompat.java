package net.krodark.asterion.port.compat;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class RecipeSerializerCompat {
    private RecipeSerializerCompat() {
    }

    public static <T extends Recipe<?>> RecipeSerializer<T> of(
            MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {
        return new RecipeSerializer<>() {
            @Override public MapCodec<T> codec() { return codec; }
            @Override public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() { return streamCodec; }
        };
    }

    /**
     * Creates a serializer for a stateless recipe. The exact same recipe instance must back both
     * codecs: {@link StreamCodec#unit(Object)} rejects a different (even equivalent) instance while
     * synchronizing recipes to a client.
     */
    public static <T extends Recipe<?>> RecipeSerializer<T> unit(T recipe) {
        return of(MapCodec.unit(recipe), StreamCodec.unit(recipe));
    }
}
