package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface PacketSender {
    Packet<?> createPacket(CustomPacketPayload payload);

    default void sendPacket(Packet<?> packet) { sendPacket(packet, null); }
    default void sendPacket(CustomPacketPayload payload) { sendPacket(createPacket(payload)); }
    void sendPacket(Packet<?> packet, PacketSendListener listener);
    default void sendPacket(CustomPacketPayload payload, PacketSendListener listener) {
        sendPacket(createPacket(payload), listener);
    }
    void disconnect(Component reason);
}
