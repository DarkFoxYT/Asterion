package net.krodark.asterion.port.legacy.network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import java.util.function.BiConsumer;
public final class ClientPlayNetworking {
 public record Context(Minecraft client,LocalPlayer player) {}
 public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type,BiConsumer<T,Context> receive){return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(type.id(),(client,handler,buf,sender)->{T message=PayloadTypeRegistry.playS2C().codec(type).decode(buf);client.execute(()->receive.accept(message,new Context(client,client.player)));});}
 public static boolean canSend(CustomPacketPayload.Type<?> type){return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(type.id());}
 public static void send(CustomPacketPayload payload){net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload.type().id(),PayloadTypeRegistry.playC2S().encode(payload));}
}
