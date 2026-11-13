package net.krodark.asterion.port.legacy;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
public abstract class BlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
 protected BlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
 // Unique names avoid overriding NeoBackports' injected modern methods.
 protected void asterionSaveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag);}
 protected void asterionLoadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.load(tag);}
 @Override protected final void saveAdditional(CompoundTag tag){asterionSaveAdditional(tag, level == null ? null : level.registryAccess());}
 @Override public final void load(CompoundTag tag){asterionLoadAdditional(tag,level == null ? null : level.registryAccess());}
 public CompoundTag getUpdateTag(HolderLookup.Provider registries){return saveWithoutMetadata();}
 @Override public final CompoundTag getUpdateTag(){return getUpdateTag(level == null ? null : level.registryAccess());}
 public final CompoundTag saveCustomOnly(HolderLookup.Provider registries){return saveWithoutMetadata();}
}
