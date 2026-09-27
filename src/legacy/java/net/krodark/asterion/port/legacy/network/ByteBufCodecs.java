package net.krodark.asterion.port.legacy.network;
import net.minecraft.network.FriendlyByteBuf;
public final class ByteBufCodecs {
 public static final StreamCodec<FriendlyByteBuf,Integer> INT=StreamCodec.of(FriendlyByteBuf::writeInt,FriendlyByteBuf::readInt);
 public static final StreamCodec<FriendlyByteBuf,Integer> VAR_INT=StreamCodec.of(FriendlyByteBuf::writeVarInt,FriendlyByteBuf::readVarInt);
 public static final StreamCodec<FriendlyByteBuf,Float> FLOAT=StreamCodec.of(FriendlyByteBuf::writeFloat,FriendlyByteBuf::readFloat);
 public static final StreamCodec<FriendlyByteBuf,Boolean> BOOL=StreamCodec.of(FriendlyByteBuf::writeBoolean,FriendlyByteBuf::readBoolean);
}
