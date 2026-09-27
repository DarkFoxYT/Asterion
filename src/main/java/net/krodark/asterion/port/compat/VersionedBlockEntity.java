package net.krodark.asterion.port.compat;
/** Common extension point for the version-specific serialization bridge. */
public abstract class VersionedBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
 protected VersionedBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
   net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state){super(type,pos,state);}
}
