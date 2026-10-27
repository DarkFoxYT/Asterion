package net.krodark.asterion.compat;

//? if <26.1 {
/*import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

public record LegacyRecipeSerializer<T extends Recipe<?>>(MapCodec<T> codec,
        StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) implements RecipeSerializer<T> { }
*///?}
