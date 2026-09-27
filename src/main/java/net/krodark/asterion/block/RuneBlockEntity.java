package net.krodark.asterion.block;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class RuneBlockEntity extends net.krodark.asterion.port.compat.VersionedBlockEntity implements GeoBlockEntity {
    private AnimatableInstanceCache animationCache;
    private float glowPercent;
    private boolean worldGenerated;
    private int beetleSpawnDelay = 200;

    public boolean isWorldGenerated() { return worldGenerated; }
    public void setWorldGenerated(boolean value) { worldGenerated = value; setChanged(); }

    @Override protected void saveAdditional(net.minecraft.nbt.CompoundTag out, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(out, registries);
        out.putBoolean("worldGenerated", worldGenerated);
    }
    @Override protected void loadAdditional(net.minecraft.nbt.CompoundTag in, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(in, registries);

        worldGenerated = net.krodark.asterion.port.compat.NbtCompat.getBoolean(in, "worldGenerated", false);
    }
    public RuneBlockEntity(BlockPos pos, BlockState state) { super(Asterion.RUNE_BLOCK_ENTITY, pos, state); }

    public static void tick(Level level, BlockPos pos, BlockState state, RuneBlockEntity rune) {
        if (level.isClientSide()) {
            float target = state.getValue(RuneBlock.POWERED) ? 100F : 0F;
            if (rune.glowPercent != target) {
                rune.glowPercent += (target - rune.glowPercent) * .18F;
                if (Math.abs(target - rune.glowPercent) < .08F) rune.glowPercent = target;
            }
            return;
        }
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 20) == 0)
            level.scheduleTick(pos, state.getBlock(), 1);
        if (level instanceof ServerLevel server && rune.worldGenerated && --rune.beetleSpawnDelay <= 0) {
            rune.beetleSpawnDelay = 600 + server.getRandom().nextInt(600);
            rune.spawnBeetle(server, pos);
        }
    }

    private void spawnBeetle(ServerLevel level, BlockPos root) {
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)) return;
        if (level.players().stream().noneMatch(player -> !player.isSpectator()
                && player.distanceToSqr(root.getX() + .5, root.getY(), root.getZ() + .5) < 48 * 48)) return;
        if (level.getEntitiesOfClass(net.krodark.asterion.entity.RuneBeetleEntity.class,
                new net.minecraft.world.phys.AABB(root).inflate(32)).size() >= 2) return;
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos pos = root.offset(level.getRandom().nextInt(13) - 6, level.getRandom().nextInt(5) - 2,
                    level.getRandom().nextInt(13) - 6);
            if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                    || !level.getFluidState(pos).isEmpty()) continue;
            var beetle = Asterion.RUNE_BEETLE.create(level);
            if (beetle == null) return;

            int carriedRune = runeIndex();
            if (level.getRandom().nextFloat() < 0.35F)
                carriedRune = (carriedRune + 1 + level.getRandom().nextInt(Asterion.RUNE_TABLETS.length - 1))
                        % Asterion.RUNE_TABLETS.length;
            beetle.setRuneIndex(carriedRune);
            beetle.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            if (!level.noCollision(beetle) || !level.isUnobstructed(beetle)) continue;
            level.addFreshEntity(beetle);
            return;
        }
    }

    public void interact(Player player, ItemStack key) {
        if (!(level instanceof ServerLevel) || !player.mayBuild()) return;
        boolean reset = key.isEmpty() && player.isShiftKeyDown();
        boolean matches = key.is(Asterion.RUNE_TABLETS[runeIndex()])
                || key.is(Asterion.RUNE_STONE_BLOCKS[runeIndex()].asItem());
        if (!reset && !matches) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.asterion.rune_key_required",
                    net.minecraft.network.chat.Component.translatable(Asterion.RUNE_TABLETS[runeIndex()].getDescriptionId())), true);
            return;
        }
        boolean powered = !reset;
        if (getBlockState().getValue(RuneBlock.POWERED) == powered) return;
        RuneBlock.setPowered(level, worldPosition, getBlockState().getValue(RuneBlock.FACING), powered);
        setChanged();
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.BLOCKS, .7F, powered ? 1.2F : .7F);
    }

    public float glowPercent() { return glowPercent; }
    public int glowColor() { return 0xFFFF9A3D; }
    public int runeIndex() { return getBlockState().getBlock() instanceof RuneBlock block ? block.runeIndex() : 0; }

    @Override public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() {
        if (animationCache == null) animationCache = GeckoLibUtil.createInstanceCache(this);
        return animationCache;
    }
}
