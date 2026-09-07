package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.entity.MinotaurAnimationController;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

public final class FlashbackMinotaurGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var boss = Asterion.MINOTAUR.create(client.level, EntitySpawnReason.COMMAND);
                try {
                    data(boss, "DATA_PHASE", MinotaurEntity.BehaviorPhase.ROAMING.ordinal());
                    boss.setDeltaMovement(Vec3.ZERO);
                    boss.tickCount = 1;
                    boss.prepareReplayAnimation(100, new Vec3(0, 80, 0));
                    boss.prepareReplayAnimation(100.5, new Vec3(.1, 80, 0));
                    check(boss.animationState() == MinotaurEntity.AnimationState.WALK, "Replay motion without entity ticks stayed idle");
                    double walk = boss.replayPoseSeconds(boss.animationState());
                    boss.prepareReplayAnimation(101, new Vec3(.2, 80, 0));
                    check(boss.replayPoseSeconds(boss.animationState()) != walk, "Replay gait froze");
                    double paused = boss.replayPoseSeconds(boss.animationState());
                    boss.prepareReplayAnimation(101, new Vec3(.2, 80, 0));
                    check(boss.replayPoseSeconds(boss.animationState()) == paused, "Paused replay advanced");
                    data(boss, "DATA_PHASE", MinotaurEntity.BehaviorPhase.BOSS.ordinal());
                    check(boss.animationState() == MinotaurEntity.AnimationState.CHASE, "Replay chase did not run");
                    var attacks = Class.forName(MinotaurEntity.class.getName() + "$BossAttack");
                    for (Object attack : attacks.getEnumConstants()) if (attack.toString().equals("PUNCH_SINGLE"))
                        data(boss, "DATA_BOSS_ATTACK", ((Enum<?>)attack).ordinal());
                    data(boss, "DATA_BOSS_ATTACK_TICKS", 25);
                    boss.prepareReplayAnimation(120.5, new Vec3(.2, 80, 0));
                    double hit = boss.replayPoseSeconds(boss.animationState());
                    data(boss, "DATA_BOSS_ATTACK_TICKS", 5);
                    boss.prepareReplayAnimation(100.5, new Vec3(0, 80, 0));
                    double rewind = boss.replayPoseSeconds(boss.animationState());
                    check(rewind < hit, "Attack pose did not rewind");
                    data(boss, "DATA_BOSS_ATTACK_TICKS", 25);
                    boss.prepareReplayAnimation(120.5, new Vec3(.2, 80, 0));
                    check(boss.replayPoseSeconds(boss.animationState()) == hit, "Same replay frame produced different pose");
                    check(MinotaurAnimationController.sampleSeconds(100.25, 2, true) == .25,
                            "Idle loop clamped at final frame");
                    boss.prepareReplayAnimation(Double.NaN, boss.position());
                    check(!boss.hasReplayAnimationClock(), "Replay clock leaked into normal play");
                    var pose = MinotaurEntity.class.getDeclaredField("clientAnimationPose");
                    pose.setAccessible(true);
                    pose.set(boss, MinotaurEntity.AnimationState.PUNCH_SINGLE);
                    boss.prepareReplayAnimation(Double.NaN, boss.position());
                    check(pose.get(boss) == MinotaurEntity.AnimationState.PUNCH_SINGLE, "Normal render reset attack clock");
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            Asterion.LOGGER.info("PASS: replay movement without entity ticks, running, pause, attack rewind, deterministic resampling and normal-play clock isolation");
        }
    }
    private static void data(MinotaurEntity boss, String name, int value) throws ReflectiveOperationException {
        var field = MinotaurEntity.class.getDeclaredField(name);
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var accessor = (EntityDataAccessor<Integer>)field.get(null);
        boss.getEntityData().set(accessor, value);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
