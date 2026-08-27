package net.krodark.labyrinth.network.ragdoll;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-validated impact against a fragile world block. */
public record RagdollBlockImpactPayload(int x, int y, int z, float energy,
                                        float directionX, float directionY, float directionZ)
        implements CustomPacketPayload {
    public static final Type<RagdollBlockImpactPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("labyrinth", "ragdoll_block_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RagdollBlockImpactPayload> CODEC =
            CustomPacketPayload.codec(RagdollBlockImpactPayload::write, RagdollBlockImpactPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); buf.writeFloat(energy);
        buf.writeFloat(directionX); buf.writeFloat(directionY); buf.writeFloat(directionZ);
    }

    private static RagdollBlockImpactPayload read(RegistryFriendlyByteBuf buf) {
        return new RagdollBlockImpactPayload(buf.readInt(), buf.readInt(), buf.readInt(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}


