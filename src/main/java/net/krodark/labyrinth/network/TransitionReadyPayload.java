package net.krodark.labyrinth.network;

import net.krodark.labyrinth.Labyrinth;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client acknowledgement sent only after the destination loaded and the blackout fully faded. */
public record TransitionReadyPayload() implements CustomPacketPayload {
    public static final TransitionReadyPayload INSTANCE = new TransitionReadyPayload();
    public static final Type<TransitionReadyPayload> TYPE = new Type<>(Labyrinth.id("transition_ready"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransitionReadyPayload> CODEC =
            CustomPacketPayload.codec((payload, buffer) -> { }, buffer -> INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
