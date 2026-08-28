package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Authoritative gateway placement for the client-only dimensional surface. */
public record GatewayPortalPayload(boolean active, BlockPos center, int surfaceY, long visualSeed)
        implements CustomPacketPayload {
    public static final Type<GatewayPortalPayload> TYPE = new Type<>(Asterion.id("gateway_portal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GatewayPortalPayload> CODEC =
            CustomPacketPayload.codec(GatewayPortalPayload::write, GatewayPortalPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeBlockPos(center);
        buffer.writeVarInt(surfaceY);
        buffer.writeLong(visualSeed);
    }

    private static GatewayPortalPayload read(RegistryFriendlyByteBuf buffer) {
        return new GatewayPortalPayload(buffer.readBoolean(), buffer.readBlockPos(), buffer.readVarInt(), buffer.readLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
