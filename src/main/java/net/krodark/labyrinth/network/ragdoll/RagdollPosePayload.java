package net.krodark.labyrinth.network.ragdoll;

import java.util.ArrayList;
import java.util.List;
import net.krodark.labyrinth.Labyrinth;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RagdollPosePayload(int entityId, int sequence, List<Part> parts)
        implements CustomPacketPayload {
    public static final Type<RagdollPosePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Labyrinth.MOD_ID, "ragdoll_pose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RagdollPosePayload> CODEC =
            CustomPacketPayload.codec(RagdollPosePayload::write, RagdollPosePayload::read);

    private void write(RegistryFriendlyByteBuf b) {
        b.writeVarInt(entityId); b.writeVarInt(sequence);
        b.writeVarInt(Math.min(16, parts.size()));
        for (int i = 0; i < Math.min(16, parts.size()); i++) parts.get(i).write(b);
    }
    private static RagdollPosePayload read(RegistryFriendlyByteBuf b) {
        int entity = b.readVarInt(), sequence = b.readVarInt();
        int count = Math.min(16, Math.max(0, b.readVarInt()));
        List<Part> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) parts.add(Part.read(b));
        return new RagdollPosePayload(entity, sequence, List.copyOf(parts));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Part(int region, float x, float y, float z,
                       float qx, float qy, float qz, float qw,
                       float vx, float vy, float vz) {
        void write(RegistryFriendlyByteBuf b) {
            b.writeVarInt(region);
            b.writeFloat(x); b.writeFloat(y); b.writeFloat(z);
            b.writeFloat(qx); b.writeFloat(qy); b.writeFloat(qz); b.writeFloat(qw);
            b.writeFloat(vx); b.writeFloat(vy); b.writeFloat(vz);
        }
        static Part read(RegistryFriendlyByteBuf b) {
            return new Part(b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readFloat());
        }
    }
}

