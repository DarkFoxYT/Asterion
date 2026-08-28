package net.krodark.asterion.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.GreekRune;
import net.krodark.asterion.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class RuneBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private boolean activated;
    private boolean solved;
    private int glowColor = 0xFFFF9A3D;
    private int wrongGlowTicks;
    private float glowPercent;

    public RuneBlockEntity(BlockPos pos, BlockState state) { super(Asterion.RUNE_BLOCK_ENTITY, pos, state); }

    public static void tick(Level level, BlockPos pos, BlockState state, RuneBlockEntity rune) {
        float target = rune.activated ? 100.0F : 0.0F;
        rune.glowPercent += (target - rune.glowPercent) * 0.18F;
        if (Math.abs(target - rune.glowPercent) < 0.08F) rune.glowPercent = target;
        if (!level.isClientSide() && rune.wrongGlowTicks > 0 && --rune.wrongGlowTicks == 0) {
            rune.activated = false;
            rune.sync();
        }
    }

    public boolean activate(Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) return false;
        int color = activationColor(stack);
        if (color == 0 || solved) return false;
        activated = true;
        glowColor = color;
        int index = getBlockState().getBlock() instanceof RuneBlock block ? block.runeIndex() : 0;
        boolean correct = index == GreekRune.forRadius(worldPosition.getX(), worldPosition.getZ()).ordinal();
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (correct) {
            solved = true;
            wrongGlowTicks = 0;
            WorldGenerator.solveRuneRoom(serverLevel, worldPosition, serverPlayer, color);
        } else {
            wrongGlowTicks = 32;
            WorldGenerator.failRuneRoom(serverLevel, worldPosition, serverPlayer);
        }
        sync();
        return true;
    }

    public void markSolved(int color) {
        solved = true;
        activated = true;
        glowColor = color;
        wrongGlowTicks = 0;
        sync();
    }

    public boolean isSolved() { return solved; }
    public float glowPercent() { return glowPercent; }
    public int glowColor() { return glowColor; }
    public int runeIndex() { return getBlockState().getBlock() instanceof RuneBlock block ? block.runeIndex() : 0; }

    public static int activationColor(ItemStack stack) {
        if (stack.is(Items.RESIN_CLUMP)) return 0xFFFF8A32;
        if (stack.is(Items.REDSTONE)) return 0xFFFF2828;
        if (stack.is(Items.GLOWSTONE_DUST)) return 0xFFFFD85A;
        if (stack.is(Items.AMETHYST_SHARD)) return 0xFFD26CFF;
        if (stack.is(Items.ECHO_SHARD)) return 0xFF35E8D0;
        if (stack.is(Items.LAPIS_LAZULI)) return 0xFF397DFF;
        return 0;
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        activated = input.getBooleanOr("activated", false);
        solved = input.getBooleanOr("solved", false);
        glowColor = input.getIntOr("glow_color", 0xFFFF9A3D);
        wrongGlowTicks = input.getIntOr("wrong_glow_ticks", 0);
        glowPercent = activated ? 100.0F : 0.0F;
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("activated", activated);
        output.putBoolean("solved", solved);
        output.putInt("glow_color", glowColor);
        output.putInt("wrong_glow_ticks", wrongGlowTicks);
    }

    @Override public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
}
