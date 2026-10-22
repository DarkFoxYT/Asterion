package net.krodark.asterion.port.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.sinytra.fabric.networking_api.NeoNetworkRegistrar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Resolve our codecs on every return path, including newer NeoForge branches. */
@Mixin(value = NetworkRegistry.class, remap = false)
public abstract class NativePayloadCodecMixin {
    @ModifyReturnValue(method = "getCodec", at = @At("RETURN"))
    private static StreamCodec<? super FriendlyByteBuf, ? extends CustomPacketPayload> asterion$codec(
            StreamCodec<? super FriendlyByteBuf, ? extends CustomPacketPayload> codec,
            ResourceLocation id, ConnectionProtocol protocol, PacketFlow flow) {
        if (codec != NeoNetworkRegistrar.DUMMY_CODEC || !id.getNamespace().equals("asterion")) return codec;
        var registered = NeoNetworkRegistrar.getPayloadRegistry(protocol, flow).get(id);
        if (registered == null) return codec;
        return (StreamCodec) registered.codec();
    }
}
