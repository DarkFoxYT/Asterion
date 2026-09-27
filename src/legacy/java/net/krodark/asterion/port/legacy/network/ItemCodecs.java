package net.krodark.asterion.port.legacy.network;
public final class ItemCodecs { public static final StreamCodec<net.minecraft.network.FriendlyByteBuf,net.minecraft.world.item.ItemStack> STACK=StreamCodec.of(net.minecraft.network.FriendlyByteBuf::writeItem,net.minecraft.network.FriendlyByteBuf::readItem); }
