package net.krodark.asterion.network.ragdoll;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TumbleExitPayload(double x, double y, double z, double vx, double vy, double vz, boolean finished)
        implements CustomPacketPayload {
    public TumbleExitPayload(double x, double y, double z, double vx, double vy, double vz) {
        this(x, y, z, vx, vy, vz, true);
    }
    public static final Type<TumbleExitPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("asterion", "tumble_exit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TumbleExitPayload> CODEC =
            CustomPacketPayload.codec(TumbleExitPayload::write, TumbleExitPayload::read);

    private void write(RegistryFriendlyByteBuf b) {
        b.writeDouble(x); b.writeDouble(y); b.writeDouble(z);
        b.writeDouble(vx); b.writeDouble(vy); b.writeDouble(vz);
        b.writeBoolean(finished);
    }

    private static TumbleExitPayload read(RegistryFriendlyByteBuf b) {
        return new TumbleExitPayload(b.readDouble(), b.readDouble(), b.readDouble(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

