package net.krodark.asterion.entity;

import net.krodark.asterion.game.ChainLiftContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;

public final class LiftCallRuneEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> ANCHOR = SynchedEntityData.defineId(LiftCallRuneEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> UPPER = SynchedEntityData.defineId(LiftCallRuneEntity.class, EntityDataSerializers.BOOLEAN);
    public LiftCallRuneEntity(EntityType<? extends LiftCallRuneEntity> type, Level level) { super(type, level); setNoGravity(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { data.define(ANCHOR, BlockPos.ZERO); data.define(UPPER, false); }
    public void configure(BlockPos anchor, boolean upper) { entityData.set(ANCHOR, anchor); entityData.set(UPPER, upper); }
    @Override public boolean isPickable() { return true; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public InteractionResult interactAt(Player player, net.minecraft.world.phys.Vec3 hit, InteractionHand hand) {
        if (player.isSpectator() || player.distanceToSqr(this) > 36) return InteractionResult.PASS;
        if (!level().isClientSide()) {
            BlockPos anchor = entityData.get(ANCHOR);
            for (ChainLiftEntity lift : level().getEntitiesOfClass(ChainLiftEntity.class,
                    new AABB(anchor).inflate(2, 130, 2))) {
                if (lift.anchor().equals(anchor)) { lift.callTo(entityData.get(UPPER)); break; }
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override public void tick() {
        super.tick();
        BlockPos anchor = entityData.get(ANCHOR);
        if (!level().isClientSide() && tickCount % 40 == 0
                && level().hasChunk(anchor.getX() >> 4, anchor.getZ() >> 4)
                && !level().getBlockState(anchor).is(ChainLiftContent.ANCHOR)) discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag out) { out.putLong("Anchor", entityData.get(ANCHOR).asLong()); out.putBoolean("Upper", entityData.get(UPPER)); }
    @Override public void readAdditionalSaveData(CompoundTag in) { configure(BlockPos.of(net.krodark.asterion.port.compat.NbtCompat.getLong(in, "Anchor", 0)), net.krodark.asterion.port.compat.NbtCompat.getBoolean(in, "Upper", false)); }
}

