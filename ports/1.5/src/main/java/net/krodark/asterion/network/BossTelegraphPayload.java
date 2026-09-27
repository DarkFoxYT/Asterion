package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record BossTelegraphPayload(Vec3 center, Vec3 direction, float radius,
                                   int durationTicks, int kind, int ownerId, float arcRadians,
                                   float halfWidth, float progress) implements CustomPacketPayload {
    public static final int HALF_ARENA_SWEEP = 0;
    public static final int TARGET_CIRCLE = 1;
    public static final int CHARGE_LANE = 2;
    public static final int FRONT_CONE = 3;
    public static final int BOX = 4;
    public static final int BOX_CONE = 5;
    public static final Type<BossTelegraphPayload> TYPE = new Type<>(Asterion.id("boss_telegraph"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossTelegraphPayload> CODEC =
            CustomPacketPayload.codec(BossTelegraphPayload::write, BossTelegraphPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        writeVec(buffer, center);
        writeVec(buffer, direction);
        buffer.writeFloat(radius);
        buffer.writeVarInt(durationTicks);
        buffer.writeVarInt(kind);
        buffer.writeVarInt(ownerId);
        buffer.writeFloat(arcRadians);
        buffer.writeFloat(halfWidth);
        buffer.writeFloat(progress);
    }

    private static BossTelegraphPayload read(RegistryFriendlyByteBuf buffer) {
        return new BossTelegraphPayload(readVec(buffer), readVec(buffer), buffer.readFloat(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat());
    }

    private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) {
        b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z);
    }

    private static Vec3 readVec(RegistryFriendlyByteBuf b) {
        return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
