package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A small command, validated against the block and player distance on the server. */
public record CrucibleControlPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final int COOL = -1;
    public static final int HEAT = 1;
    public static final int NEXT_MOLD = 2;
    public static final int POUR = 3;
    /** Actions 16-51 feed one item from the matching player inventory slot. */
    public static final int INSERT_SLOT_BASE = 16;
    public static final int REMOVE_MATERIAL_BASE = 64;
    public static final int SELECT_MOLD_BASE = 80;
    public static int insertSlot(int slot) { return INSERT_SLOT_BASE + slot; }
    public static boolean isInsertSlot(int action) {
        return action >= INSERT_SLOT_BASE && action < INSERT_SLOT_BASE + 36;
    }
    public static int inventorySlot(int action) { return action - INSERT_SLOT_BASE; }
    public static int removeMaterial(int layer) { return REMOVE_MATERIAL_BASE + layer; }
    public static boolean isRemoveMaterial(int action) {
        return action >= REMOVE_MATERIAL_BASE && action < REMOVE_MATERIAL_BASE + 4;
    }
    public static int materialLayer(int action) { return action - REMOVE_MATERIAL_BASE; }
    public static int selectMold(int mold) { return SELECT_MOLD_BASE + mold; }
    public static boolean isSelectMold(int action) {
        return action >= SELECT_MOLD_BASE
                && action < SELECT_MOLD_BASE + net.krodark.asterion.block.CrucibleBlockEntity.Mold.values().length;
    }
    public static int moldIndex(int action) { return action - SELECT_MOLD_BASE; }
    public static final Type<CrucibleControlPayload> TYPE = new Type<>(Asterion.id("crucible_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CrucibleControlPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> { buffer.writeBlockPos(payload.pos); buffer.writeByte(payload.action); },
            buffer -> new CrucibleControlPayload(buffer.readBlockPos(), buffer.readByte()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
