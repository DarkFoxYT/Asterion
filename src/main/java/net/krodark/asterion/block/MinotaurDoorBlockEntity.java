package net.krodark.asterion.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.DoorBreakPayload;
import net.krodark.asterion.network.MazeShiftPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MinotaurDoorBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean unlocked;
    private boolean unlockedWithKey;
    private boolean breaching;
    private long motionStart;
    private float startAngle, targetAngle;
    private float previousDustAngle = Float.NaN;

    public MinotaurDoorBlockEntity(BlockPos pos, BlockState state) { super(Asterion.MINOTAUR_DOOR_BLOCK_ENTITY, pos, state); }
    public Direction facing() { return getBlockState().getValue(MinotaurDoorBlock.FACING); }
    public boolean allowsArenaEntry() {
        return unlockedWithKey && !breaching && getBlockState().getValue(MinotaurDoorBlock.OPEN);
    }
    public float angle(float partialTick) {
        if (level == null) return targetAngle;
        float elapsed = Math.max(0, level.getGameTime() - motionStart) + partialTick;
        return breaching ? MinotaurDoorMotion.breachAngle(elapsed)
                : startAngle + (targetAngle - startAngle) * MinotaurDoorMotion.ease(elapsed / MinotaurDoorMotion.OPEN_TICKS);
    }
    public float movementRumble(float partialTick) {
        if (level == null || breaching) return 0;
        float t = (level.getGameTime() - motionStart + partialTick) / MinotaurDoorMotion.OPEN_TICKS;
        if (t <= 0 || t >= 1) return 0;
        float travel = Math.abs(targetAngle - startAngle) / MinotaurDoorMotion.OPEN_ANGLE;
        return (float)Math.sin(t * Math.PI) * Math.min(1F, travel);
    }
    public void interact(Player player, ItemStack held) {
        if (level == null || breaching) return;
        // The arena's north door is a boss exit, never a second keyed player entrance.
        if (level.dimension().equals(Asterion.ASTERION_LEVEL)
                && worldPosition.equals(net.krodark.asterion.worldgen.MinotaurArenaEntrances.door(
                        net.krodark.asterion.worldgen.MinotaurArenaEntrances.BOSS_ENTRANCE))) return;
        if (net.krodark.asterion.worldgen.BossArenaEncounter.sealsDoor(level, worldPosition, facing())) return;
        boolean insertedKey = held.is(Asterion.MINOTAUR_KEY) && !unlockedWithKey;
        if (insertedKey) {
            unlockedWithKey = true;
            unlocked = true;
            if (!player.isCreative()) held.shrink(1);
            if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    &&worldPosition.equals(net.krodark.asterion.worldgen.MinotaurArenaEntrances.door(
                    net.krodark.asterion.worldgen.MinotaurArenaEntrances.PLAYER_ENTRANCE)))
                net.krodark.asterion.WorldGenerator.requestBossArenaStart(serverPlayer);
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1F, .65F);
            sync();
        }
        if (!unlocked) {
            if (!held.is(Asterion.MINOTAUR_KEY) && !player.isCreative()) {
                player.sendSystemMessage(Component.translatable("message.asterion.minotaur_door_locked"));
                level.playSound(null, worldPosition, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, .5F, .6F);
                return;
            }
            unlocked = true;
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1F, .65F);
        }
        if (targetAngle > 0 && occupied()) return;
        startAngle = angle(0);
        targetAngle = targetAngle > 0 ? 0 : MinotaurDoorMotion.OPEN_ANGLE;
        motionStart = level.getGameTime();
        // Keep the passage passable throughout closing; occupancy is checked again before latching.
        MinotaurDoorBlock.setOpen(level, worldPosition, facing(), true);
        sync();
    }
    private boolean occupied() {
        BlockPos a = MinotaurDoorBlock.part(worldPosition, facing(), 0, 0);
        BlockPos b = MinotaurDoorBlock.part(worldPosition, facing(), 6, 4);
        return !level.getEntities((net.minecraft.world.entity.Entity)null, AABB.encapsulatingFullBlocks(a, b), e -> e.isAlive() && !e.isSpectator()).isEmpty();
    }
    public void closeForEncounter() {
        if (level == null || breaching || targetAngle == 0) return;
        startAngle = angle(0);
        targetAngle = 0;
        motionStart = level.getGameTime();
        sync();
    }
    public void beginBreach() {
        if (level == null || level.isClientSide() || breaching) return;
        breaching = true;
        motionStart = level.getGameTime();
        startAngle = targetAngle = 0;
        MinotaurDoorBlock.setOpen(level, worldPosition, facing(), false);
        sync();
    }
    public void breakOff() {
        if (!(level instanceof ServerLevel server)) return;
        Direction facing = facing();
        DoorBreakPayload payload = new DoorBreakPayload(worldPosition, facing, MinotaurDoorMotion.BREAK_ANGLE, server.getGameTime());
        for (var player : server.players()) if (player.distanceToSqr(Vec3.atCenterOf(worldPosition)) < 96 * 96) {
            if (ServerPlayNetworking.canSend(player, DoorBreakPayload.TYPE)) ServerPlayNetworking.send(player, payload);
            if (ServerPlayNetworking.canSend(player, MazeShiftPayload.TYPE))
                ServerPlayNetworking.send(player, new MazeShiftPayload(worldPosition, 64, 1.8F, 22));
        }
        server.playSound(null, worldPosition, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 3.5F, .55F);
        server.playSound(null, worldPosition, Asterion.METAL_HIT, SoundSource.BLOCKS, 2.5F, .85F);
        Vec3 inward = facing.getOpposite().getUnitVec3();
        Vec3 across = facing.getClockWise().getUnitVec3();
        Vec3 smokeCenter = Vec3.atBottomCenterOf(worldPosition).add(inward.scale(1.15D)).add(0, 3.2D, 0);
        server.sendParticles(Asterion.DOOR_SMOKE, smokeCenter.x, smokeCenter.y, smokeCenter.z,
                110, 3.8D, 3.1D, 2.2D, 0.075D);
        server.sendParticles(ParticleTypes.DUST_PLUME, smokeCenter.x, smokeCenter.y - 1.2D, smokeCenter.z,
                55, 3.2D, 2.1D, 1.7D, 0.12D);
        for (int i = 0; i < 18; i++) {
            double side = (server.getRandom().nextDouble() - .5) * 6;
            Vec3 fragment = Vec3.atBottomCenterOf(worldPosition).add(across.scale(side))
                    .add(0, .5 + server.getRandom().nextDouble() * 4.5, 0);
            net.krodark.asterion.worldgen.ArenaDebris.queue(server, fragment,
                    inward.scale(.45 + server.getRandom().nextDouble() * .65)
                            .add(across.scale(side * .07)).add(0, .25 + server.getRandom().nextDouble() * .4, 0));
        }
        MinotaurDoorBlock.removeDoor(level, worldPosition, facing);
    }
    public static void tick(Level level, BlockPos pos, BlockState state, MinotaurDoorBlockEntity door) {
        long elapsed = level.getGameTime() - door.motionStart;
        if (level.isClientSide()) { door.scrapeDust(); return; }
        if (door.breaching) {
            if (elapsed == 14 || elapsed == 44 || elapsed == 78) {
                level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.BLOCKS, 2.6F, .5F);
                if (level instanceof ServerLevel server) {
                    for (var viewer : server.players()) if (viewer.distanceToSqr(Vec3.atCenterOf(pos)) < 64 * 64
                            && ServerPlayNetworking.canSend(viewer, MazeShiftPayload.TYPE))
                        ServerPlayNetworking.send(viewer, new MazeShiftPayload(pos, 72,
                                elapsed == 78 ? 1.15F : elapsed == 44 ? .72F : .48F, 16));
                    server.sendParticles(Asterion.DOOR_DUST, pos.getX() + .5, pos.getY() + .18, pos.getZ() + .5,
                            elapsed == 78 ? 54 : 30, 3.2, .18, .7, .055);
                    door.dropCeilingRubble(server, elapsed == 78 ? 42 : elapsed == 44 ? 24 : 14);
                }
            }
            if (elapsed >= MinotaurDoorMotion.BREAK_TICK) door.breakOff();
        } else if (state.getValue(MinotaurDoorBlock.OPEN) && door.targetAngle == 0
                && elapsed >= MinotaurDoorMotion.OPEN_TICKS) {
            if (door.occupied()) {
                door.startAngle = 0;
                door.targetAngle = MinotaurDoorMotion.OPEN_ANGLE;
                door.motionStart = level.getGameTime();
            } else MinotaurDoorBlock.setOpen(level, pos, door.facing(), false);
            door.sync();
        }
    }
    private void dropCeilingRubble(ServerLevel level, int count) {
        Direction acrossDirection = facing().getClockWise();
        Vec3 across = acrossDirection.getUnitVec3();
        Vec3 inward = facing().getOpposite().getUnitVec3();
        Vec3 ceiling = Vec3.atBottomCenterOf(worldPosition).add(0, 22.5D, 0).add(inward.scale(2.0D));
        for (int i = 0; i < count; i++) {
            double side = (level.getRandom().nextDouble() - .5D) * 9.0D;
            Vec3 origin = ceiling.add(across.scale(side)).add(inward.scale(level.getRandom().nextDouble() * 4.0D));
            net.krodark.asterion.worldgen.ArenaDebris.queue(level, origin,
                    new Vec3((level.getRandom().nextDouble() - .5D) * .12D,
                            -.32D - level.getRandom().nextDouble() * .28D,
                            (level.getRandom().nextDouble() - .5D) * .12D));
        }
        level.sendParticles(ParticleTypes.DUST_PLUME, ceiling.x, ceiling.y, ceiling.z,
                count * 2, 4.5D, .7D, 3.0D, .04D);
    }
    private void scrapeDust() {
        long elapsed = level.getGameTime() - motionStart;
        if (breaching && elapsed >= 78 && elapsed < MinotaurDoorMotion.BREAK_TICK) {
            Vec3 inward = facing().getOpposite().getUnitVec3();
            for (int i = 0; i < 3; i++) {
                Vec3 point = Vec3.atBottomCenterOf(worldPosition).add(inward.scale(.65))
                        .add(facing().getClockWise().getUnitVec3().scale((level.getRandom().nextDouble() - .5) * 6));
                level.addParticle(Asterion.DOOR_SMOKE, true, false, point.x, point.y + .25, point.z,
                        inward.x * .04, .05 + level.getRandom().nextDouble() * .03, inward.z * .04);
            }
        }
        float current = angle(0);
        float movement = Float.isFinite(previousDustAngle) ? Math.abs(current - previousDustAngle) : 0;
        previousDustAngle = current;
        if (movement < .001F) return;
        int count = Math.clamp((int)(movement * 60), 1, 5);
        for (int side = -1; side <= 1; side += 2) {
            Vec3 point = MinotaurDoorMotion.emitter(worldPosition, facing(), side, current);
            for (int i = 0; i < count; i++) level.addParticle(Asterion.DOOR_DUST,
                    point.x + (level.getRandom().nextFloat() - .5) * .24, point.y,
                    point.z + (level.getRandom().nextFloat() - .5) * .24,
                    (level.getRandom().nextFloat() - .5) * .045, .006, (level.getRandom().nextFloat() - .5) * .045);
        }
    }
    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putBoolean("unlocked", unlocked); out.putBoolean("breaching", breaching);
        out.putBoolean("unlockedWithKey", unlockedWithKey);
        out.putLong("motionStart", motionStart); out.putFloat("startAngle", startAngle); out.putFloat("targetAngle", targetAngle);
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        unlocked = in.getBooleanOr("unlocked", false); breaching = in.getBooleanOr("breaching", false);
        unlockedWithKey = in.getBooleanOr("unlockedWithKey", false);
        motionStart = in.getLongOr("motionStart", 0);
        startAngle = in.getFloatOr("startAngle", 0); targetAngle = in.getFloatOr("targetAngle", 0);
        previousDustAngle = Float.NaN;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
