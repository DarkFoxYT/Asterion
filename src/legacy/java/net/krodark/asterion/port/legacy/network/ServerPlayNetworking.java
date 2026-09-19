package net.krodark.asterion.port.legacy.network;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.BiConsumer;
public final class ServerPlayNetworking {
 public record Context(MinecraftServer server,ServerPlayer player) {}
 public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type,BiConsumer<T,Context> receive){return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(type.id(),(server,player,handler,buf,sender)->{T message=PayloadTypeRegistry.playC2S().codec(type).decode(buf);server.execute(()->receive.accept(message,new Context(server,player)));});}
 public static boolean canSend(ServerPlayer player,CustomPacketPayload.Type<?> type){return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,type.id());}
 public static void send(ServerPlayer player,CustomPacketPayload payload){net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,payload.type().id(),PayloadTypeRegistry.playS2C().encode(payload));}
}
