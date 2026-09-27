package net.krodark.asterion.port.legacy.network;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
public final class PayloadTypeRegistry {
 private static final PayloadTypeRegistry S2C=new PayloadTypeRegistry(),C2S=new PayloadTypeRegistry();
 private final Map<ResourceLocation,StreamCodec<FriendlyByteBuf,?>> codecs=new HashMap<>();
 public static PayloadTypeRegistry playS2C(){return S2C;}
 public static PayloadTypeRegistry playC2S(){return C2S;}
 public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,StreamCodec<FriendlyByteBuf,T> codec) {if(codecs.putIfAbsent(type.id(),codec)!=null) throw new IllegalStateException("Duplicate packet "+type.id());}
 @SuppressWarnings("unchecked") public <T extends CustomPacketPayload> StreamCodec<FriendlyByteBuf,T> codec(CustomPacketPayload.Type<T> type){return (StreamCodec<FriendlyByteBuf,T>)Objects.requireNonNull(codecs.get(type.id()),"Unregistered packet "+type.id());}
 public <T extends CustomPacketPayload> FriendlyByteBuf encode(T payload){var b=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create(); @SuppressWarnings("unchecked") var type=(CustomPacketPayload.Type<T>)payload.type();codec(type).encode(b,payload);return b;}
}
