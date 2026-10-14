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

    public boolean isOpening() { return openingTicks > 0; }

    public boolean unlock(Player player) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || openingTicks > 0
                || getBlockState().getValue(OmegaLockBlock.UNLOCKED)) return false;
        gates = findNearbyGates(server, worldPosition);
        if (gates.isEmpty()) return false;
        openingTicks = 1;
        if (worldPosition.equals(net.krodark.asterion.worldgen.MinotaurArenaEntrances.OMEGA_LOCK_POSITION))
            net.krodark.asterion.AsterionWorldState.get(server).markOmegaGateUnlocked();
        server.setBlock(worldPosition, getBlockState().setValue(OmegaLockBlock.UNLOCKED, true), Block.UPDATE_ALL);
        server.playSound(null, worldPosition, SoundEvents.VAULT_ACTIVATE, SoundSource.BLOCKS, 1.5F, .55F);
        for (var viewer : server.players()) if (viewer.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) < 64 * 64
                && ServerPlayNetworking.canSend(viewer, MazeShiftPayload.TYPE))
            ServerPlayNetworking.send(viewer, new MazeShiftPayload(worldPosition, 64, .42F, 18));
        // The first gate row and the invisible lock state share this server tick.
        openNextRow(server, worldPosition, this);
        setChanged();
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OmegaLockBlockEntity lock) {
        if (level.isClientSide() || lock.openingTicks <= 0 || !(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        if (++lock.openingTicks % 10 == 1) openNextRow(server, pos, lock);
    }

    private static void openNextRow(net.minecraft.server.level.ServerLevel server, BlockPos pos, OmegaLockBlockEntity lock) {
        if (lock.gates.isEmpty()) lock.gates = findNearbyGates(server, pos);
        int lowest = Integer.MAX_VALUE;
        for (BlockPos gatePos : lock.gates) {
            if (server.getChunkSource().getChunkNow(gatePos.getX() >> 4, gatePos.getZ() >> 4) == null) return;
            BlockState gate = server.getBlockState(gatePos);
            if (gate.is(Asterion.MAZESTEEL_GATE) && !gate.getValue(DirectionalGateBlock.OPEN))
                lowest = Math.min(lowest, gatePos.getY());
        }
        if (lowest == Integer.MAX_VALUE) {
            lock.openingTicks = 0;
            server.removeBlock(pos, false);
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
            if (lock.openingTicks > 1) for (var viewer : server.players()) if (viewer.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 56 * 56
                    && ServerPlayNetworking.canSend(viewer, MazeShiftPayload.TYPE))
                ServerPlayNetworking.send(viewer, new MazeShiftPayload(pos, 56, .16F, 9));
        }
        lock.setChanged();
    }

    private static List<BlockPos> findNearbyGates(net.minecraft.server.level.ServerLevel level, BlockPos center) {
        var pending = new java.util.ArrayDeque<BlockPos>();
        var seen = new java.util.HashSet<BlockPos>();
        var found = new ArrayList<BlockPos>();
        for (var direction : net.minecraft.core.Direction.values()) pending.add(center.relative(direction));
        collectConnectedGates(level, center, pending, seen, found);
        if (found.isEmpty()) {
            // Preserve detached placements, but search only sections which already contain gates.
            var nearby = net.krodark.asterion.util.LoadedBlockSearch.find(level, center.offset(-12,-8,-12),
                    center.offset(12,12,12), state -> state.is(Asterion.MAZESTEEL_GATE));
            nearby.stream().min(Comparator.comparingDouble(p -> p.distSqr(center))).ifPresent(pending::add);
            seen.clear();
            collectConnectedGates(level, center, pending, seen, found);
        }
        found.sort(Comparator.comparingInt(BlockPos::getY));
        return List.copyOf(found);
    }

    private static void collectConnectedGates(net.minecraft.server.level.ServerLevel level, BlockPos center,
            java.util.ArrayDeque<BlockPos> pending, java.util.Set<BlockPos> seen, List<BlockPos> found) {
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            if (!seen.add(pos) || Math.abs(pos.getX()-center.getX()) > 12 || Math.abs(pos.getZ()-center.getZ()) > 12
                    || pos.getY() < center.getY()-8 || pos.getY() > center.getY()+12
                    || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) continue;
            if (!level.getBlockState(pos).is(Asterion.MAZESTEEL_GATE)) continue;
            found.add(pos);
            for (var direction : net.minecraft.core.Direction.values()) pending.add(pos.relative(direction));
        }
    }

    @Override protected void saveAdditional(ValueOutput out) { super.saveAdditional(out); out.putInt("openingTicks", openingTicks); }
    @Override protected void loadAdditional(ValueInput in) { super.loadAdditional(in); openingTicks = in.getIntOr("openingTicks", 0); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
