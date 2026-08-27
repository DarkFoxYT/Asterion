package net.krodark.labyrinth.network.ragdoll;

import net.krodark.labyrinth.Labyrinth;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** One scene-wide physical blast event for already active ragdolls. */
public record RagdollExplosionPayload(Vec3 center, float radius) implements CustomPacketPayload {
    public static final Type<RagdollExplosionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Labyrinth.MOD_ID, "ragdoll_explosion"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RagdollExplosionPayload> CODEC =
            CustomPacketPayload.codec(RagdollExplosionPayload::write, RagdollExplosionPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(center.x);
        buffer.writeDouble(center.y);
        buffer.writeDouble(center.z);
        buffer.writeFloat(radius);
    }

    private static RagdollExplosionPayload read(RegistryFriendlyByteBuf buffer) {
        return new RagdollExplosionPayload(new Vec3(buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble()), buffer.readFloat());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}


