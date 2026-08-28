package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Dimension-wide, non-attenuated arrival roar. */
public record EntryOmenPayload() implements CustomPacketPayload {
    public static final EntryOmenPayload INSTANCE = new EntryOmenPayload();
    public static final Type<EntryOmenPayload> TYPE = new Type<>(Asterion.id("entry_omen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntryOmenPayload> CODEC =
            CustomPacketPayload.codec((payload, buffer) -> { }, buffer -> INSTANCE);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
