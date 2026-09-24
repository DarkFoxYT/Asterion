package net.krodark.asterion.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Signed directional input from the player in the ferry's control seat. */
public record FerryControlPayload(int entityId, int throttle, int turn) implements CustomPacketPayload {
    public static final Type<FerryControlPayload> TYPE = new Type<>(Asterion.id("ferry_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FerryControlPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeVarInt(payload.entityId);
                buffer.writeByte(payload.throttle);
                buffer.writeByte(payload.turn);
            }, buffer -> new FerryControlPayload(buffer.readVarInt(), buffer.readByte(), buffer.readByte()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> context.server().execute(() -> {
            if (context.player().level().getEntity(payload.entityId()) instanceof CharonsFerryEntity ferry)
                ferry.receiveControl(context.player(), payload.throttle(), payload.turn());
        }));
    }
}
