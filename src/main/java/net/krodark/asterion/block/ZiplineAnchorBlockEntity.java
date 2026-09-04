package net.krodark.asterion.block;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.zipline.ZiplineSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class ZiplineAnchorBlockEntity extends BlockEntity {
    private BlockPos other;
    private net.minecraft.world.phys.Vec3 attachment;
    private net.minecraft.world.phys.Vec3 otherAttachment;
    private String chain = "minecraft:iron_chain";
    public ZiplineAnchorBlockEntity(BlockPos pos, BlockState state) { super(Asterion.ZIPLINE_ANCHOR_ENTITY, pos, state); }
    public void link(BlockPos endpoint, net.minecraft.world.phys.Vec3 here,
                     net.minecraft.world.phys.Vec3 there, String chainId) {
        other = endpoint.immutable(); attachment = here; otherAttachment = there; chain = chainId; setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    public BlockPos other() { return other; }
    public net.minecraft.world.phys.Vec3 attachment() {
        return attachment == null ? worldPosition.getCenter() : attachment;
    }
    public net.minecraft.world.phys.Vec3 otherAttachment() {
        return otherAttachment == null ? (other == null ? attachment() : other.getCenter()) : otherAttachment;
    }
    public String chainId() { return chain; }
    public boolean primary() { return other != null && worldPosition.asLong() < other.asLong(); }
    @Override public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level); ZiplineSystem.register(this);
    }
    @Override public void setRemoved() { ZiplineSystem.unregister(this); super.setRemoved(); }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out); if (other != null) out.putLong("other", other.asLong()); out.putString("chain", chain);
        if (attachment != null) { out.putDouble("attachX", attachment.x); out.putDouble("attachY", attachment.y); out.putDouble("attachZ", attachment.z); }
        if (otherAttachment != null) { out.putDouble("otherX", otherAttachment.x); out.putDouble("otherY", otherAttachment.y); out.putDouble("otherZ", otherAttachment.z); }
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in); long packed = in.getLongOr("other", Long.MIN_VALUE);
        other = packed == Long.MIN_VALUE ? null : BlockPos.of(packed); chain = in.getStringOr("chain", "minecraft:iron_chain");
        double attachX = in.getDoubleOr("attachX", Double.NaN);
        attachment = Double.isNaN(attachX) ? null : new net.minecraft.world.phys.Vec3(attachX,
                in.getDoubleOr("attachY", worldPosition.getY() + .5D),
                in.getDoubleOr("attachZ", worldPosition.getZ() + .5D));
        double otherX = in.getDoubleOr("otherX", Double.NaN);
        otherAttachment = Double.isNaN(otherX) ? null : new net.minecraft.world.phys.Vec3(otherX,
                in.getDoubleOr("otherY", worldPosition.getY() + .5D),
                in.getDoubleOr("otherZ", worldPosition.getZ() + .5D));
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
