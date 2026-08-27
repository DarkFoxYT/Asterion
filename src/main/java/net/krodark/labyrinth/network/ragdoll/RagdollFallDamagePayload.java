package net.krodark.labyrinth.network.ragdoll;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RagdollFallDamagePayload(float damage) implements CustomPacketPayload {
    public static final Type<RagdollFallDamagePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("labyrinth", "ragdoll_fall_damage"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RagdollFallDamagePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.FLOAT, RagdollFallDamagePayload::damage,
                    RagdollFallDamagePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

