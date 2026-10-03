package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ChainHoldPayload(boolean held) implements CustomPacketPayload {
    public static final Type<ChainHoldPayload> TYPE=new Type<>(Asterion.id("chain_hold"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ChainHoldPayload> CODEC=StreamCodec.of(
            (out,value)->out.writeBoolean(value.held),in->new ChainHoldPayload(in.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void initialize() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(TYPE,CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(TYPE,(payload,context)->
                context.server().execute(()->net.krodark.asterion.physics.ChainGrip.setHeld(context.player(),payload.held())));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->
                net.krodark.asterion.physics.ChainGrip.release(handler.getPlayer()));
    }
}
