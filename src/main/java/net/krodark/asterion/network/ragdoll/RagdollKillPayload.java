package net.krodark.asterion.network.ragdoll;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RagdollKillPayload(int entityId) implements CustomPacketPayload {
    public static final Type<RagdollKillPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("asterion", "ragdoll_kill"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RagdollKillPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, RagdollKillPayload::entityId, RagdollKillPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

