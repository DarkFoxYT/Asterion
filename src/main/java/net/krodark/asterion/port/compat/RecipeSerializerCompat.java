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
            //? if >=1.20.5 {
            public MapCodec<T> codec() { return codec; }
            @Override public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() { return streamCodec; }
            //?} else {
            /*@Override public T fromJson(net.minecraft.resources.ResourceLocation id, com.google.gson.JsonObject json) {
                return codec.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow(false, error -> {throw new com.google.gson.JsonParseException(error);});
            }
            @Override public T fromNetwork(net.minecraft.resources.ResourceLocation id, net.minecraft.network.FriendlyByteBuf buffer) { return streamCodec.decode(buffer); }
            @Override public void toNetwork(net.minecraft.network.FriendlyByteBuf buffer, T recipe) { streamCodec.encode(buffer, recipe); }*/
            //?}
        };
    }

    /**
     * Creates a serializer for a stateless recipe. The exact same recipe instance must back both
     * codecs: {@link StreamCodec#unit(Object)} rejects a different (even equivalent) instance while
     * synchronizing recipes to a client.
     */
    public static <T extends Recipe<?>> RecipeSerializer<T> unit(T recipe) {

//? if >=1.20.5 {
return of(MapCodec.unit(recipe), StreamCodec.unit(recipe));
//?} else {
/*return new RecipeSerializer<T>() {
 @SuppressWarnings("unchecked") private T instance(net.minecraft.resources.ResourceLocation id){
  if(recipe instanceof net.krodark.asterion.recipe.ForgedSwordRecipe)return (T)new net.krodark.asterion.recipe.ForgedSwordRecipe(id);
  if(recipe instanceof net.krodark.asterion.recipe.RemovedRecipe)return (T)new net.krodark.asterion.recipe.RemovedRecipe(id);
  throw new IllegalArgumentException("No legacy stateless recipe constructor: "+recipe.getClass());
 }
 @Override public T fromJson(net.minecraft.resources.ResourceLocation id,com.google.gson.JsonObject json){return instance(id);}
 @Override public T fromNetwork(net.minecraft.resources.ResourceLocation id,net.minecraft.network.FriendlyByteBuf buffer){return instance(id);}
 @Override public void toNetwork(net.minecraft.network.FriendlyByteBuf buffer,T recipe){}
};*/
//?}

    }
}
