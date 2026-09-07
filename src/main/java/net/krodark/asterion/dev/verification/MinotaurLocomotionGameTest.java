package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

public final class MinotaurLocomotionGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var boss = Asterion.MINOTAUR.create(client.level, EntitySpawnReason.COMMAND);
                try {
                    var update = MinotaurEntity.class.getDeclaredMethod("updateClientLocomotion");
                    update.setAccessible(true);
                    boss.setPos(100, 100, 100);
                    boss.setDeltaMovement(Vec3.ZERO);
                    update.invoke(boss);
                    expect(boss, "IDLE");
                    data(boss, "DATA_PHASE", MinotaurEntity.BehaviorPhase.ROAMING.ordinal());
                    for (int i = 0; i < 20; i++) {
                        boss.tickCount++;
                        boss.setPos(boss.getX() + .025, 100, 100);
                        update.invoke(boss);
                        expect(boss, "WALK");
                    }
                    // Empty velocity and a short gap in position packets must not reset the gait.
                    for (int i = 0; i < 4; i++) {
                        boss.tickCount++;
                        update.invoke(boss);
                        expect(boss, "WALK");
                    }
                    data(boss, "DATA_PHASE", MinotaurEntity.BehaviorPhase.CHASING.ordinal());
                    expect(boss, "CHASE");
                    data(boss, "DATA_PHASE", MinotaurEntity.BehaviorPhase.BOSS.ordinal());
                    attack(boss, "RETRIEVE_AXE");
                    expect(boss, "CHASE");
                    attack(boss, "GRAB");
                    expect(boss, "CHASE");
                    attack(boss, "SWORD_COMBO");
                    expect(boss, "SWORD");
                    attack(boss, "NONE");
                    boss.tickCount += 6;
                    // Stale remote velocity must not leave a stationary boss running in place.
                    boss.setDeltaMovement(.3, 0, 0);
                    update.invoke(boss);
                    expect(boss, "IDLE");
                    boss.setDeltaMovement(Vec3.ZERO);
                    boss.tickCount++;
                    boss.setPos(101, 100, 100);
                    update.invoke(boss);
                    expect(boss, "CHASE");
                    boss.tickCount++;
                    boss.setPos(300, 100, 300);
                    update.invoke(boss);
                    expect(boss, "IDLE");
                    data(boss, "DATA_DOOR_ENTRY_TICKS", 1);
                    expect(boss, "IDLE");
                    Asterion.LOGGER.info("PASS: zero-velocity remote walking, packet gaps, running, moving grab/axe retrieval, attack priority, stopping and teleports");
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
        }
    }
    private static void data(MinotaurEntity boss, String name, int value) throws ReflectiveOperationException {
        var field = MinotaurEntity.class.getDeclaredField(name);
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var accessor = (EntityDataAccessor<Integer>)field.get(null);
        boss.getEntityData().set(accessor, value);
    }
    private static void attack(MinotaurEntity boss, String name) throws ReflectiveOperationException {
        var type = Class.forName(MinotaurEntity.class.getName() + "$BossAttack");
        for (Object value : type.getEnumConstants()) if (value.toString().equals(name)) {
            data(boss, "DATA_BOSS_ATTACK", ((Enum<?>)value).ordinal());
            return;
        }
        throw new AssertionError(name);
    }
    private static void expect(MinotaurEntity boss, String name) {
        if (!boss.animationState().toString().equals(name))
            throw new AssertionError("Expected " + name + ", got " + boss.animationState());
    }
}
