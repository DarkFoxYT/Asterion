package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record QueenBeetleQuestPayload(int stage, int progress, int target) implements CustomPacketPayload {
    public static final int ACCEPTED = 0;
    public static final int PROGRESS = 1;
    public static final int REWARDED = 2;
    public static final int COMPLETE = 3;
    public static final int RESTORE_ACTIVE = 4;
    public static final Type<QueenBeetleQuestPayload> TYPE = new Type<>(Asterion.id("queen_beetle_quest"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QueenBeetleQuestPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.stage);
                buffer.writeVarInt(payload.progress);
                buffer.writeVarInt(payload.target);
            },
            buffer -> new QueenBeetleQuestPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
