package net.krodark.asterion.network.ragdoll;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RagdollArmorImpactPayload(int region, float energy) implements CustomPacketPayload {
    public static final Type<RagdollArmorImpactPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Asterion.MOD_ID, "ragdoll_armor_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RagdollArmorImpactPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, RagdollArmorImpactPayload::region,
                    ByteBufCodecs.FLOAT, RagdollArmorImpactPayload::energy,
                    RagdollArmorImpactPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

