package net.krodark.asterion.network.ragdoll;

import java.util.UUID;
import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-owned lifetime; UUID prevents a reused entity id from hiding a different player. */
public record RagdollStatePayload(int entityId, UUID playerId, boolean active) implements CustomPacketPayload {
    public static final Type<RagdollStatePayload> TYPE = new Type<>(Asterion.id("ragdoll_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RagdollStatePayload> CODEC = CustomPacketPayload.codec(
            (p, b) -> { b.writeVarInt(p.entityId); b.writeUUID(p.playerId); b.writeBoolean(p.active); },
            b -> new RagdollStatePayload(b.readVarInt(), b.readUUID(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
