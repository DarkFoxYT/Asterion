package net.fabricmc.fabric.api.networking.v1;

import io.netty.buffer.Unpooled;

import net.minecraft.network.FriendlyByteBuf;

public final class PacketByteBufs {
    private PacketByteBufs() {}

    public static FriendlyByteBuf create() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }
}
