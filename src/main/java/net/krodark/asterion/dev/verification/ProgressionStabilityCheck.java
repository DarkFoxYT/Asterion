package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionWorldState;
import net.krodark.asterion.block.DirectionalGateBlock;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.krodark.asterion.worldgen.MinotaurArenaEntrances;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class ProgressionStabilityCheck {
    static void run(MinecraftServer server) {
        var level = server.overworld();
        var mount = Asterion.SCARLET_CENTIPEDE.create(level, EntitySpawnReason.COMMAND);
        try {
            var attach = ScarletCentipedeEntity.class.getDeclaredMethod("setAttachedSurface", Direction.class);
            attach.setAccessible(true);
            attach.invoke(mount, Direction.EAST);
            var heading = ScarletCentipedeEntity.class.getDeclaredField("surfaceForward");
            heading.setAccessible(true);
            heading.set(mount, new Vec3(0, 1, 0));
            var blend = ScarletCentipedeEntity.class.getDeclaredMethod("blendAttachmentNormal");
            blend.setAccessible(true);
            for (int i = 0; i < 30; i++) blend.invoke(mount);
            check(((Vec3)heading.get(mount)).distanceTo(new Vec3(0, 1, 0)) < 1e-8,
                    "Visual settling rotated wall-climbing controls");
            mount.setPos(10 - mount.getBbWidth() * .5 - .03, 180.03, .5);
            mount.tickCount = 100;
            for (BlockPos p : BlockPos.betweenClosed(6, 179, -3, 10, 179, 3)) level.setBlock(p, Blocks.STONE.defaultBlockState(), 18);
            for (BlockPos p : BlockPos.betweenClosed(10, 180, -3, 10, 190, 3)) level.setBlock(p, Blocks.STONE.defaultBlockState(), 18);
            var motion = ScarletCentipedeEntity.class.getDeclaredField("smoothedSurfaceMotion");
            motion.setAccessible(true);
            motion.set(mount, new Vec3(0, .2, 0));
            mount.setDeltaMovement(0, -.3, 0);
            var touching = ScarletCentipedeEntity.class.getDeclaredMethod("touchingSurface", Direction.class);
            touching.setAccessible(true);
            check((boolean)touching.invoke(mount, Direction.DOWN) && (boolean)touching.invoke(mount, Direction.EAST),
                    "Corner fixture must touch both wall and floor");
            var transition = ScarletCentipedeEntity.class.getDeclaredMethod("bestTransitionSurface", Direction.class);
            transition.setAccessible(true);
            check(transition.invoke(mount, Direction.EAST) == null, "Adhesion switched the mount back onto the floor");

            var maze = server.getLevel(Asterion.ASTERION_LEVEL);
            var world = AsterionWorldState.get(maze);
            var freshState = new AsterionWorldState();
            freshState.markOmegaGateUnlocked();
            freshState.resetMinotaurEncounter();
            check(!freshState.omegaGateUnlocked(), "Encounter restart left the Omega gate unlocked");
            var unlocked = AsterionWorldState.class.getDeclaredField("omegaGateUnlocked");
            unlocked.setAccessible(true);
            boolean previous = world.omegaGateUnlocked();
            try {
                unlocked.setBoolean(world, false);
                MinotaurArenaEntrances.setAuthoredBossGate(maze, 0);
                var gate = MinotaurArenaEntrances.OMEGA_LOCK_POSITION.above();
                check(!maze.getBlockState(gate).getValue(DirectionalGateBlock.OPEN), "Gate reset bypassed the Omega lock");
                world.markOmegaGateUnlocked();
                MinotaurArenaEntrances.setAuthoredBossGate(maze, 0);
                check(maze.getBlockState(gate).getValue(DirectionalGateBlock.OPEN), "Unlocked Omega gate did not reopen");
            } finally { unlocked.setBoolean(world, previous); }
            Asterion.LOGGER.info("PASS: visual wall settling preserves heading, adhesion cannot switch to the floor, and Omega gate resets respect its lock");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
