package net.krodark.asterion.network.ragdoll;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RagdollEntityImpactPayload(int entityId, float x, float y, float z)
        implements CustomPacketPayload {
    public static final Type<RagdollEntityImpactPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("asterion", "ragdoll_entity_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RagdollEntityImpactPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, RagdollEntityImpactPayload::entityId,
                    ByteBufCodecs.FLOAT, RagdollEntityImpactPayload::x,
                    ByteBufCodecs.FLOAT, RagdollEntityImpactPayload::y,
                    ByteBufCodecs.FLOAT, RagdollEntityImpactPayload::z,
                    RagdollEntityImpactPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

