package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DoorBreakPayload(BlockPos root, Direction facing, float angle, long seed) implements CustomPacketPayload {
    public static final Type<DoorBreakPayload> TYPE = new Type<>(Asterion.id("door_break"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DoorBreakPayload> CODEC = StreamCodec.of(
            (out, p) -> { out.writeBlockPos(p.root); out.writeEnum(p.facing); out.writeFloat(p.angle); out.writeLong(p.seed); },
            in -> new DoorBreakPayload(in.readBlockPos(), in.readEnum(Direction.class), in.readFloat(), in.readLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
