package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

 
public record CrucibleScreenPayload(BlockPos pos, int temperature, int targetTemperature,
                                    int heatControl, int fuelTicks, int mold, int mixColor, int materialUnits,
                                    String metalSequence, int autoPourProgress) implements CustomPacketPayload {
    public static final Type<CrucibleScreenPayload> TYPE = new Type<>(Asterion.id("crucible_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CrucibleScreenPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeVarInt(payload.temperature);
                buffer.writeVarInt(payload.targetTemperature);
                buffer.writeVarInt(payload.heatControl);
                buffer.writeVarInt(payload.fuelTicks);
                buffer.writeVarInt(payload.mold);
                buffer.writeInt(payload.mixColor);
                buffer.writeVarInt(payload.materialUnits);
                buffer.writeUtf(payload.metalSequence, 5);
                buffer.writeVarInt(payload.autoPourProgress);
            }, buffer -> new CrucibleScreenPayload(buffer.readBlockPos(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readInt(),
                    buffer.readVarInt(), buffer.readUtf(5), buffer.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
