package net.krodark.asterion.port.legacy;
public final class BlockCodecs {
 public static <T> com.mojang.serialization.MapCodec<T> simple(java.util.function.Function<net.minecraft.world.level.block.state.BlockBehaviour.Properties,T> factory){return com.mojang.serialization.MapCodec.unit(()->factory.apply(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));}
}
