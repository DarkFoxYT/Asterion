package net.krodark.asterion.block;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.GameplayContent;
import net.krodark.asterion.game.GasClouds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.*;

public final class TimedTrapBlockEntity extends BlockEntity {
    private int seconds, remaining, burst;
    public TimedTrapBlockEntity(BlockPos pos, BlockState state) { super(GameplayContent.TRAP_ENTITY, pos, state); }
    public int periodSeconds() { return seconds == 0 ? 5 : seconds; }
    public void setPeriodSeconds(int seconds) { this.seconds = Math.clamp(seconds, 1, 60); remaining = this.seconds * 20; setChanged(); }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out); out.putInt("PeriodSeconds", seconds); out.putInt("RemainingTicks", remaining); out.putInt("BurstTicks", burst);
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in); seconds = Math.clamp(in.getIntOr("PeriodSeconds", 0), 0, 60);
        remaining = Math.clamp(in.getIntOr("RemainingTicks", seconds * 20), 0, 1200); burst = Math.clamp(in.getIntOr("BurstTicks", 0), 0, 8);
    }
    public static void tick(Level world, BlockPos pos, BlockState state, TimedTrapBlockEntity trap) {
        if (!(world instanceof ServerLevel level)) return;
        if (trap.seconds == 0) trap.setPeriodSeconds(1 + level.getRandom().nextInt(60));
        if (--trap.remaining <= 0) {
            trap.remaining = trap.seconds * 20; trap.burst = 8;
            level.setBlock(pos, state.setValue(TimedTrapBlock.ACTIVE, true), 3);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.BLOCKS, .8F, 1.25F);
        }
        Vec3 direction = state.getValue(TimedTrapBlock.FACING).getUnitVec3();
        Vec3 start = Vec3.atCenterOf(pos).add(direction.scale(.56));
        if (trap.remaining == 8) level.sendParticles(Asterion.GREEK_FIRE_SOOT, start.x, start.y, start.z, 5, .1, .1, .1, .01);
        if (trap.burst > 0) {
            boolean first = trap.burst == 8;
            if (((TimedTrapBlock)state.getBlock()).gas()) {
                if (trap.burst % 2 == 0) GasClouds.emit(level, start, direction.scale(.3), null);
            } else {
                var hit = level.clip(new net.minecraft.world.level.ClipContext(start, start.add(direction.scale(7)),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                        net.minecraft.world.phys.shapes.CollisionContext.empty()));
                Vec3 end = hit.getLocation();
                double length = start.distanceTo(end);
                for (double distance = 0; distance < length; distance += .6) {
                    Vec3 point = start.add(direction.scale(distance));
                    level.sendParticles(Asterion.GREEK_FIRE, point.x, point.y, point.z, 0, direction.x, direction.y, direction.z, .35);
                }
                if (first) for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(.4))) {
                    if (victim.getBoundingBox().inflate(.3).clip(start, end).isEmpty()
                            && !victim.getBoundingBox().inflate(.3).contains(start)) continue;
                    victim.hurtServer(level, level.damageSources().inFire(), 14);
                    net.krodark.asterion.effect.GreekFireBurn.ignite(victim, 5);
                }
            }
            if (--trap.burst == 0) level.setBlock(pos, state.setValue(TimedTrapBlock.ACTIVE, false), 3);
        }
        // Persist the countdown on chunk save, without block update packets every tick.
        trap.setChanged();
    }
}
