package net.krodark.labyrinth.network.ragdoll;

import net.krodark.labyrinth.Labyrinth;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record RagdollImpulsePayload(Vec3 source, Vec3 impulse, float force)
        implements CustomPacketPayload {
    public static final Type<RagdollImpulsePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Labyrinth.MOD_ID, "ragdoll_impulse"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RagdollImpulsePayload> CODEC =
            CustomPacketPayload.codec(RagdollImpulsePayload::write, RagdollImpulsePayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        writeVec(buffer, source);
        writeVec(buffer, impulse);
        buffer.writeFloat(force);
    }

    private static RagdollImpulsePayload read(RegistryFriendlyByteBuf buffer) {
        return new RagdollImpulsePayload(readVec(buffer), readVec(buffer), buffer.readFloat());
    }

    private static void writeVec(RegistryFriendlyByteBuf buffer, Vec3 value) {
        buffer.writeDouble(value.x);
        buffer.writeDouble(value.y);
        buffer.writeDouble(value.z);
    }

    private static Vec3 readVec(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

