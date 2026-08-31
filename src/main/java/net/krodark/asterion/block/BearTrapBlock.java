package net.krodark.asterion.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;

public final class BearTrapBlock extends Block {
    public BearTrapBlock(Properties properties) { super(properties); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return box(1, 0, 1, 15, 3, 15); }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity)) return;
        entity.makeStuckInBlock(state, new Vec3(.8, .75, .8));
        Vec3 movement = entity.isClientAuthoritative() ? entity.getKnownMovement() : entity.oldPosition().subtract(entity.position());
        if (level instanceof ServerLevel server && (Math.abs(movement.x) >= .003 || Math.abs(movement.z) >= .003))
            entity.hurtServer(server, level.damageSources().sweetBerryBush(), 1);
    }
}
