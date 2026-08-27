package net.krodark.labyrinth.network;

import net.krodark.labyrinth.Labyrinth;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DimensionTransitionPayload(int fadeInTicks, int holdTicks) implements CustomPacketPayload {
    public static final Type<DimensionTransitionPayload> TYPE = new Type<>(Labyrinth.id("dimension_transition"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DimensionTransitionPayload> CODEC =
            CustomPacketPayload.codec(DimensionTransitionPayload::write, DimensionTransitionPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(fadeInTicks);
        buffer.writeVarInt(holdTicks);
    }

    private static DimensionTransitionPayload read(RegistryFriendlyByteBuf buffer) {
        return new DimensionTransitionPayload(buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
