package net.krodark.asterion.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

public final class CursedBrazierDoorBlockEntity extends BlockEntity implements GeoBlockEntity {
    public static final int MOVE_TICKS = 90;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float progress;
    private float startProgress;
    private boolean raising;
    private boolean moving;
    private boolean passageEntered;
    private boolean unlocked;
    private boolean victoryOpen;
    private long motionStart;

    public CursedBrazierDoorBlockEntity(BlockPos pos, BlockState state) {
        super(Asterion.CURSED_BRAZIER_DOOR_BLOCK_ENTITY, pos, state);
    }
    public float progress(float partialTick) {
        if (!moving || level == null) return progress;
        float t = Math.clamp((level.getGameTime() - motionStart + partialTick) / MOVE_TICKS, 0F, 1F);
        t = t * t * (3F - 2F * t);
        return startProgress + ((raising ? 1F : 0F) - startProgress) * t;
    }
    public void toggle(Player player, ItemStack held) {
        if (level == null || moving || victoryOpen) return;
        if (!unlocked) {
            if (!held.is(net.krodark.asterion.game.GameplayContent.CURSED_BRAZIER_KEY) && !player.isCreative()) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                        "message.asterion.cursed_brazier_door_locked"));
                level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.CHAIN_HIT,
                        net.minecraft.sounds.SoundSource.BLOCKS, .9F, .55F);
                return;
            }
            unlocked = true;
            if (!player.isCreative()) held.shrink(1);
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.VAULT_ACTIVATE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.3F, .68F);
        }
        begin(!getBlockState().getValue(CursedBrazierDoorBlock.OPEN));
    }

    public void sealForFight() {
        if (level == null) return;
        unlocked = true;
        victoryOpen = false;
        passageEntered = false;
        if (progress(0) > 0.001F || getBlockState().getValue(CursedBrazierDoorBlock.OPEN)) {
            begin(false);
        } else {
            CursedBrazierDoorBlock.setOpen(level, worldPosition,
                    getBlockState().getValue(CursedBrazierDoorBlock.FACING), false);
            sync();
        }
    }

    public void openAfterVictory() {
        if (level == null) return;
        unlocked = true;
        victoryOpen = true;
        passageEntered = false;
        if (progress(0) < 0.999F || !getBlockState().getValue(CursedBrazierDoorBlock.OPEN)) {
            begin(true);
        } else {
            CursedBrazierDoorBlock.setOpen(level, worldPosition,
                    getBlockState().getValue(CursedBrazierDoorBlock.FACING), true);
            sync();
        }
    }

    private void begin(boolean open) {
        startProgress = progress(0);
        raising = open;
        moving = true;
        motionStart = level.getGameTime();
        passageEntered = false;
        if (open) CursedBrazierDoorBlock.setOpen(level, worldPosition,
                getBlockState().getValue(CursedBrazierDoorBlock.FACING), true);
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.PISTON_EXTEND,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.7F, .48F);
        sync();
    }
    private AABB passage() {
        var facing = getBlockState().getValue(CursedBrazierDoorBlock.FACING);
        BlockPos a = CursedBrazierDoorBlock.part(worldPosition, facing, 0, 0);
        BlockPos b = CursedBrazierDoorBlock.part(worldPosition, facing, 2, 4);
        return AABB.encapsulatingFullBlocks(a, b).inflate(.35D, 0, .35D);
    }
    public static void tick(Level level, BlockPos pos, BlockState state, CursedBrazierDoorBlockEntity door) {
        if (level.isClientSide()) return;
        if (door.moving) {
            long elapsed = level.getGameTime() - door.motionStart;
            if (level instanceof net.minecraft.server.level.ServerLevel server && elapsed % 6 == 0) {
                float height = door.progress(0) * 4.5F;
                server.sendParticles(Asterion.DOOR_DUST, pos.getX() + .5D, pos.getY() + .15D + height,
                        pos.getZ() + .5D, 7, 1.35D, .12D, .35D, .025D);
                if (elapsed % 18 == 0) level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHAIN_HIT,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.1F, .55F + elapsed / 500F);
            }
            if (elapsed >= MOVE_TICKS) {
                door.progress = door.raising ? 1F : 0F;
                door.moving = false;
                if (!door.raising) CursedBrazierDoorBlock.setOpen(level, pos,
                        state.getValue(CursedBrazierDoorBlock.FACING), false);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.35F, door.raising ? .72F : .52F);
                door.sync();
            }
            return;
        }
        if (door.victoryOpen || !state.getValue(CursedBrazierDoorBlock.OPEN)) return;
        boolean occupied = !level.getEntitiesOfClass(Player.class, door.passage(), Player::isAlive).isEmpty();
        if (occupied) door.passageEntered = true;
        else if (door.passageEntered) door.begin(false);
    }
    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putFloat("progress", progress(0)); out.putFloat("startProgress", startProgress);
        out.putBoolean("raising", raising); out.putBoolean("moving", moving);
        out.putBoolean("passageEntered", passageEntered); out.putBoolean("unlocked", unlocked);
        out.putBoolean("victoryOpen", victoryOpen);
        out.putLong("motionStart", motionStart);
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        progress = in.getFloatOr("progress", 0); startProgress = in.getFloatOr("startProgress", progress);
        raising = in.getBooleanOr("raising", false); moving = in.getBooleanOr("moving", false);
        passageEntered = in.getBooleanOr("passageEntered", false);
        unlocked = in.getBooleanOr("unlocked", false);
        victoryOpen = in.getBooleanOr("victoryOpen", false);
        motionStart = in.getLongOr("motionStart", 0);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
