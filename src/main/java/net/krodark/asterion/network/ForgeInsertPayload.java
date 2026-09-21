package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

 
public record ForgeInsertPayload(BlockPos pos, Vec3 from, ItemStack item) implements CustomPacketPayload {
    public static final Type<ForgeInsertPayload> TYPE = new Type<>(Asterion.id("forge_insert"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeInsertPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeBlockPos(p.pos); b.writeDouble(p.from.x); b.writeDouble(p.from.y);
                b.writeDouble(p.from.z); ItemStack.STREAM_CODEC.encode(b, p.item); },
            b -> new ForgeInsertPayload(b.readBlockPos(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                    ItemStack.STREAM_CODEC.decode(b)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
