package net.krodark.labyrinth.network.ragdoll;

import net.krodark.labyrinth.Labyrinth;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Server-accepted player ragdoll root and velocity for gentle reconciliation. */
public record RagdollAuthorityPayload(Vec3 position, Vec3 velocity, long serverTick)
        implements CustomPacketPayload {
    public static final Type<RagdollAuthorityPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Labyrinth.MOD_ID, "ragdoll_authority"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RagdollAuthorityPayload> CODEC =
            CustomPacketPayload.codec(RagdollAuthorityPayload::write, RagdollAuthorityPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        writeVec(buffer, position);
        writeVec(buffer, velocity);
        buffer.writeVarLong(serverTick);
    }

    private static RagdollAuthorityPayload read(RegistryFriendlyByteBuf buffer) {
        return new RagdollAuthorityPayload(readVec(buffer), readVec(buffer), buffer.readVarLong());
    }

    private static void writeVec(RegistryFriendlyByteBuf buffer, Vec3 value) {
        buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z);
    }

    private static Vec3 readVec(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}


