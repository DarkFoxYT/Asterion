package net.krodark.asterion.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;

/** A freely operated wooden door; animation state is shared with watching clients. */
public final class BarrelDoorBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final float OPEN_ANGLE = (float)(Math.PI / 2);
    private static final int MOTION_TICKS = 16;
    private long motionStart;
    private float startAngle, targetAngle;

    public BarrelDoorBlockEntity(BlockPos pos, BlockState state) {
        super(Asterion.BARREL_DOOR_BLOCK_ENTITY, pos, state);
    }
    private Direction facing() { return getBlockState().getValue(BarrelDoorBlock.FACING); }
    public float angle(float partialTick) {
        if (level == null) return targetAngle;
        float t = Math.clamp((level.getGameTime() - motionStart + partialTick) / MOTION_TICKS, 0F, 1F);
        // Quick swing, gentle overshoot, then a clean settle. Reversals start at the current pose.
        float u = t - 1F;
        float eased = 1F + 2.2F * u * u * u + 1.2F * u * u;
        return startAngle + (targetAngle - startAngle) * eased;
    }
    public void interact(Player player, ItemStack held) {
        if (level == null || level.isClientSide()) return;
        if (targetAngle > 0 && occupied()) return;
        if (targetAngle == 0 && !BarrelDoorBlock.prepareSwing(level, worldPosition, facing())) return;
        startAngle = angle(0);
        targetAngle = targetAngle > 0 ? 0 : OPEN_ANGLE;
        motionStart = level.getGameTime();
        BarrelDoorBlock.setOpen(level, worldPosition, facing(), true);
        level.playSound(null, worldPosition, targetAngle > 0 ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE,
                SoundSource.BLOCKS, 1F, targetAngle > 0 ? 1.15F : .9F);
        sync();
    }
    private boolean occupied() {
        AABB opening = AABB.encapsulatingFullBlocks(BarrelDoorBlock.part(worldPosition, facing(), 0, 0),
                BarrelDoorBlock.part(worldPosition, facing(), 2, 3));
        return !level.getEntities((net.minecraft.world.entity.Entity)null, opening,
                entity -> entity.isAlive() && !entity.isSpectator()).isEmpty();
    }
    public static void tick(Level level, BlockPos pos, BlockState state, BarrelDoorBlockEntity door) {
        if (level.isClientSide()) return;
        if (state.getValue(BarrelDoorBlock.OPEN) && door.targetAngle == 0
                && level.getGameTime() - door.motionStart >= MOTION_TICKS) {
            if (door.occupied()) {
                door.startAngle = 0;
                door.targetAngle = OPEN_ANGLE;
                door.motionStart = level.getGameTime();
            } else BarrelDoorBlock.setOpen(level, pos, door.facing(), false);
            door.sync();
        }
        // Also detect parts removed without neighbor notifications (commands/explosions).
        if (level.getGameTime() % 20 == 0) level.scheduleTick(pos, state.getBlock(), 1);
    }
    private void sync() {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("motionStart", motionStart);
        out.putFloat("startAngle", startAngle);
        out.putFloat("targetAngle", targetAngle);
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        motionStart = in.getLongOr("motionStart", 0);
        startAngle = in.getFloatOr("startAngle", 0);
        targetAngle = in.getFloatOr("targetAngle", 0);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
