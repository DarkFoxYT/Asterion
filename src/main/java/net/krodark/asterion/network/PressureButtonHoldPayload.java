package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PressureButtonHoldPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<PressureButtonHoldPayload> TYPE=new Type<>(Asterion.id("pressure_button_hold"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PressureButtonHoldPayload> CODEC=StreamCodec.of(
            (out,value)->out.writeBlockPos(value.pos),in->new PressureButtonHoldPayload(in.readBlockPos()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
