package net.krodark.asterion.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
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

public final class RuneBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private float glowPercent;
    private boolean worldGenerated;
    private int beetleSpawnDelay = 200;

    public boolean isWorldGenerated() { return worldGenerated; }
    public void setWorldGenerated(boolean value) { worldGenerated = value; setChanged(); }

    @Override protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
        super.saveAdditional(out);
        out.putBoolean("worldGenerated", worldGenerated);
    }
    @Override protected void loadAdditional(net.minecraft.world.level.storage.ValueInput in) {
        super.loadAdditional(in);
        // Unknown/legacy plaques are deliberately ineligible: their origin cannot be proven.
        worldGenerated = in.getBooleanOr("worldGenerated", false);
    }
    public RuneBlockEntity(BlockPos pos, BlockState state) { super(Asterion.RUNE_BLOCK_ENTITY, pos, state); }

    public static void tick(Level level, BlockPos pos, BlockState state, RuneBlockEntity rune) {
        float target = state.getValue(RuneBlock.POWERED) ? 100F : 0F;
        rune.glowPercent += (target - rune.glowPercent) * .18F;
        if (Math.abs(target - rune.glowPercent) < .08F) rune.glowPercent = target;
        if (!level.isClientSide() && level.getGameTime() % 20 == 0) level.scheduleTick(pos, state.getBlock(), 1);
        if (level instanceof ServerLevel server && rune.worldGenerated && --rune.beetleSpawnDelay <= 0) {
            rune.beetleSpawnDelay = 600 + server.getRandom().nextInt(600);
            rune.spawnBeetle(server, pos);
        }
    }

    private void spawnBeetle(ServerLevel level, BlockPos root) {
        if (!level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS)) return;
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
            var beetle = Asterion.RUNE_BEETLE.create(level, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
            if (beetle == null) return;
            beetle.setRuneIndex(runeIndex());
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
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.asterion.rune_key_required",
                    net.minecraft.network.chat.Component.translatable(Asterion.RUNE_TABLETS[runeIndex()].getDescriptionId())));
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
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
}
