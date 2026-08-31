package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.entity.MinotaurLeapPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Runs the real attack and native travel code over open ground, a vaultable wall and a blocked ceiling. */
final class MinotaurMotionCheck {
    static void run(ServerLevel level, ServerPlayer player) {
        Vec3 previous = player.position();
        var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
        try {
            for (int x = -36; x <= -24; x++) for (int z = -42; z <= 2; z++) {
                level.setBlock(new BlockPos(x, 120, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 121; y <= 144; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
            var attack = MinotaurEntity.class.getDeclaredMethod("tickBossAttack", ServerLevel.class, ServerPlayer.class);
            attack.setAccessible(true);
            var obstacles = MinotaurEntity.class.getDeclaredMethod("tickBossObstacleTraversal", ServerLevel.class);
            obstacles.setAccessible(true);
            var chase = MinotaurEntity.class.getDeclaredMethod("tickChase", ServerLevel.class, ServerPlayer.class);
            chase.setAccessible(true);
            var phase = MinotaurEntity.class.getDeclaredMethod("setBehaviorPhase", MinotaurEntity.BehaviorPhase.class);
            phase.setAccessible(true);
            for (int scenario = 0; scenario < 3; scenario++) {
                boolean wall = scenario > 0;
                boss.setPos(-30, 121, -35); boss.setDeltaMovement(Vec3.ZERO); boss.setOnGround(true);
                player.setPos(-30, 121, -5); player.setDeltaMovement(Vec3.ZERO);
                boss.beginDebug(player); boss.setDebugRunning(false);
                if (wall) for (int x = -36; x <= -24; x++) for (int y = 121; y <= 125; y++)
                    level.setBlock(new BlockPos(x, y, -20), Blocks.STONE.defaultBlockState(), 2);
                check(MinotaurLeapPlan.find(level, boss, player.position()) != null, "No feasible leap across test terrain");
                if (scenario == 2) phase.invoke(boss, MinotaurEntity.BehaviorPhase.CHASING);
                check(boss.forceDebugAttack(player, "leap"), "Leap unavailable");
                check(boss.animationState() == MinotaurEntity.AnimationState.LEAP, "Chase leap lost its authored pose");
                double peak = 121; int launches = 0; boolean aboveFloor = false;
                for (int tick = 0; tick < 80; tick++) {
                    if (!boss.debugStatus().contains("attack=NONE")) (scenario == 2 ? chase : attack).invoke(boss, level, player);
                    boss.getMoveControl().tick();
                    boss.travel(Vec3.ZERO);
                    boolean airborne = boss.getY() > 121.1;
                    if (!aboveFloor && airborne) launches++;
                    aboveFloor = airborne; peak = Math.max(peak, boss.getY());
                    check(!boss.horizontalCollision, "Leap struck the obstacle instead of clearing it");
                }
                check(boss.position().subtract(player.position()).horizontalDistance() < 1,
                        "Leap fell short of player: " + boss.position());
                check(launches == 1 && boss.onGround(), "Leap relaunched or failed to land: " + launches);
                if (wall) check(peak >= 126, "Leap did not clear five-block obstacle");
                Asterion.LOGGER.info("PASS: targeted 30-block leap scenario={} peak={} landing={}", scenario, peak, boss.position());
            }
            for (int x = -36; x <= -24; x++) for (int z = -42; z <= 2; z++)
                level.setBlock(new BlockPos(x, 129, z), Blocks.STONE.defaultBlockState(), 2);
            boss.setPos(-30, 121, -35); boss.setDeltaMovement(Vec3.ZERO); boss.setOnGround(true);
            boss.beginDebug(player); boss.setDebugRunning(false);
            check(MinotaurLeapPlan.find(level, boss, player.position()) == null, "Planner accepted a body arc through a low ceiling");
            for (int tick = 0; tick < 100; tick++) {
                boss.tickCount++; boss.horizontalCollision = true;
                obstacles.invoke(boss, level);
                check(boss.getDeltaMovement().y <= 0 && boss.debugStatus().contains("attack=NONE"), "Blind obstacle hop returned");
            }
            boss.forceDebugAttack(player, "leap");
            for (int tick = 0; tick < 16; tick++) attack.invoke(boss, level, player);
            check(boss.debugStatus().contains("attack=NONE") && boss.getDeltaMovement().y <= 0, "Blocked leap still launched");
            boss.horizontalCollision = false;
            boss.forceDebugAttack(player, "slam");
            for (int tick = 0; tick < MinotaurEntity.DRAW_AXE_TICKS + 44; tick++) {
                attack.invoke(boss, level, player); boss.travel(Vec3.ZERO);
                check(boss.getY() <= 121.01, "Grounded axe slam produced an empty jump");
            }
            Asterion.LOGGER.info("PASS: blocked arcs rejected, no repeated obstacle hops, grounded axe slam");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        finally { player.setPos(previous); player.setDeltaMovement(Vec3.ZERO); boss.discard(); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
