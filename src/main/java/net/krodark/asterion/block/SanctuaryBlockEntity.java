package net.krodark.asterion.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class SanctuaryBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int pulse;
    public SanctuaryBlockEntity(BlockPos pos, BlockState state) { super(RespawnObelisks.BLOCK_ENTITY, pos, state); }
    public void startPulse() { pulse = 24; setChanged(); }
    public static void tick(Level level, BlockPos pos, BlockState state, SanctuaryBlockEntity self) {
        if (!(level instanceof ServerLevel server) || self.pulse <= 0) return;
        double radius = (25 - self.pulse) * .32;
        int points = 40;
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points;
            double x = pos.getX() + .5 + Math.cos(angle) * radius;
            double z = pos.getZ() + .5 + Math.sin(angle) * radius;
            BlockPos sample = BlockPos.containing(x, pos.getY(), z);
            if (!level.getBlockState(sample).getCollisionShape(level, sample).isEmpty()) continue;
            server.sendParticles(new DustParticleOptions(0xFFD574, .65F + self.pulse / 36F),
                    x, pos.getY() + .18 + Math.sin(angle * 4) * .07, z, 1, .04, .04, .04, 0);
        }
        self.pulse--; self.setChanged();
    }
    @Override protected void saveAdditional(ValueOutput output) { super.saveAdditional(output); output.putInt("pulse", pulse); }
    @Override protected void loadAdditional(ValueInput input) { super.loadAdditional(input); pulse = Math.clamp(input.getIntOr("pulse", 0), 0, 24); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
