package net.krodark.asterion.port.legacy.network;
import net.minecraft.resources.ResourceLocation;
import java.util.function.*;
public interface CustomPacketPayload {
 record Type<T extends CustomPacketPayload>(ResourceLocation id) {}
 Type<? extends CustomPacketPayload> type();
 static <B,V> StreamCodec<B,V> codec(BiConsumer<V,B> write,Function<B,V> read){return StreamCodec.of((b,v)->write.accept(v,b),read);}
}
