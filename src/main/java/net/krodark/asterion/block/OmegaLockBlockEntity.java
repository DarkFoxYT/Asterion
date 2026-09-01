package net.krodark.asterion.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.MazeShiftPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class OmegaLockBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int openingTicks;
    private List<BlockPos> gates = List.of();

    public OmegaLockBlockEntity(BlockPos pos, BlockState state) { super(Asterion.OMEGA_LOCK_BLOCK_ENTITY, pos, state); }

    public boolean unlock(Player player) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || openingTicks > 0
                || getBlockState().getValue(OmegaLockBlock.UNLOCKED)) return false;
        gates = findNearbyGates(server, worldPosition);
        if (gates.isEmpty()) return false;
        openingTicks = 1;
        server.setBlock(worldPosition, getBlockState().setValue(OmegaLockBlock.UNLOCKED, true), Block.UPDATE_ALL);
        server.playSound(null, worldPosition, SoundEvents.VAULT_ACTIVATE, SoundSource.BLOCKS, 1.5F, .55F);
        for (var viewer : server.players()) if (viewer.distanceToSqr(worldPosition.getCenter()) < 64 * 64
                && ServerPlayNetworking.canSend(viewer, MazeShiftPayload.TYPE))
            ServerPlayNetworking.send(viewer, new MazeShiftPayload(worldPosition, 64, .42F, 18));
        setChanged();
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OmegaLockBlockEntity lock) {
        if (level.isClientSide() || lock.openingTicks <= 0 || !(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        lock.openingTicks++;
        if (lock.gates.isEmpty()) lock.gates = findNearbyGates(server, pos);
        if (lock.openingTicks % 10 != 1) return;
        int lowest = lock.gates.stream().filter(p -> {
            BlockState gate = server.getBlockState(p);
            return gate.is(Asterion.MAZESTEEL_GATE) && !gate.getValue(DirectionalGateBlock.OPEN);
        }).mapToInt(BlockPos::getY).min().orElse(Integer.MAX_VALUE);
        if (lowest == Integer.MAX_VALUE) {
            // The mechanism leaves with the raised gate. Arena repair preserves the empty
            // authored keyhole cell, so completion remains stable across future reloads.
            server.removeBlock(pos,false);
            return;
        }
        int changed = 0;
        for (BlockPos gatePos : lock.gates) if (gatePos.getY() == lowest) {
            BlockState gate = server.getBlockState(gatePos);
            if (gate.is(Asterion.MAZESTEEL_GATE) && !gate.getValue(DirectionalGateBlock.OPEN)) {
                server.setBlock(gatePos, gate.setValue(DirectionalGateBlock.OPEN, true), Block.UPDATE_ALL);
                server.sendParticles(Asterion.DOOR_DUST, gatePos.getX()+.5, gatePos.getY()+.15, gatePos.getZ()+.5,
                        5, .38, .08, .38, .025);
                changed++;
            }
        }
        if (changed > 0) {
            server.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 1.2F, .55F + lowest % 4 * .04F);
            for (var viewer : server.players()) if (viewer.distanceToSqr(pos.getCenter()) < 56 * 56
                    && ServerPlayNetworking.canSend(viewer, MazeShiftPayload.TYPE))
                ServerPlayNetworking.send(viewer, new MazeShiftPayload(pos, 56, .16F, 9));
        }
        lock.setChanged();
    }

    private static List<BlockPos> findNearbyGates(net.minecraft.server.level.ServerLevel level, BlockPos center) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-12,-8,-12), center.offset(12,12,12)))
            if (level.getBlockState(p).is(Asterion.MAZESTEEL_GATE)) found.add(p.immutable());
        found.sort(Comparator.comparingInt(BlockPos::getY));
        return List.copyOf(found);
    }

    @Override protected void saveAdditional(ValueOutput out) { super.saveAdditional(out); out.putInt("openingTicks", openingTicks); }
    @Override protected void loadAdditional(ValueInput in) { super.loadAdditional(in); openingTicks = in.getIntOr("openingTicks", 0); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
