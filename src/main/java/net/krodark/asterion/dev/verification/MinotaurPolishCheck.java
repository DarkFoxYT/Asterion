package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;

/** Native-world regressions for interruptible healing, finite gas hazards and authoritative attack facing. */
final class MinotaurPolishCheck {
    static void run(ServerLevel level, ServerPlayer player) {
        Vec3 previous = player.position();
        long previousTime = level.getGameTime();
        float previousHealth = player.getHealth();
        GameType previousMode = player.gameMode.getGameModeForPlayer();
        var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
        try {
            boss.setPos(0, 121, 0); boss.beginDebug(player); boss.setDebugRunning(false);
            var regen = method(MinotaurEntity.class, "tickRegeneration", ServerLevel.class);
            var reset = method(MinotaurEntity.class, "interruptRegeneration");
            var delays = new HashSet<Long>();
            for (int i = 0; i < 64; i++) {
                reset.invoke(boss);
                long delay = (long)field(boss, "regenerationDeadline") - level.getGameTime();
                check(delay >= 600 && delay <= 900, "Regen delay escaped 30â€“45 seconds");
                delays.add(delay);
            }
            check(delays.size() > 30, "Regen delay did not vary");
            boss.setHealth(boss.getMaxHealth() * .5F);
            float before = boss.getHealth();
            regen.invoke(boss, level);
            check(boss.getHealth() == before, "Boss healed during its no-hit grace period");
            long deadline = (long)field(boss, "regenerationDeadline");
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime((deadline / 20 + 1) * 20);
            regen.invoke(boss, level);
            check(boss.getHealth() > before && boss.getHealth() - before <= 2.001F, "Unfair or missing regeneration pulse");
            boss.invulnerableTime = 0;
            check(boss.hurtServer(level, level.damageSources().playerAttack(player), 2), "Test hit rejected");
            before = boss.getHealth();
            regen.invoke(boss, level);
            check(boss.getHealth() == before && (long)field(boss, "regenerationDeadline") >= level.getGameTime() + 600,
                    "A hit did not immediately interrupt regeneration");
            Asterion.LOGGER.info("PASS: randomized 30â€“45s regeneration, capped healing, hit interrupt");

            player.setGameMode(GameType.SURVIVAL);
            Object smoke = field(boss, "smokeClouds");
            Method emit = method(smoke.getClass(), "emit", MinotaurEntity.class, Vec3.class, Vec3.class);
            Method tick = method(smoke.getClass(), "tick", ServerLevel.class, MinotaurEntity.class);
            List<?> clouds = (List<?>)field(smoke, "clouds");
            boss.getRandom().setSeed(409);
            emit.invoke(smoke, boss, new Vec3(0, 123, 8), new Vec3(0, 0, 1));
            Object cloud = clouds.getFirst();
            int ignite = (int)field(cloud, "igniteAt");
            check(ignite >= 100 && ignite <= 160, "Smoke ignition escaped 5â€“8 seconds");
            // Two identical clouds occupy the same ground: damage must not stack within a pulse.
            boss.getRandom().setSeed(409);
            emit.invoke(smoke, boss, new Vec3(0, 123, 8), new Vec3(0, 0, 1));
            for (int age = 1; age <= ignite + 80; age++) {
                player.clearFire(); player.invulnerableTime = 0;
                for (Object gas : clouds) {
                    setField(gas, "position", new Vec3(0, 121.2, 8));
                    setField(gas, "velocity", Vec3.ZERO);
                }
                player.setPos(0, 121, 8); player.setHealth(previousHealth);
                tick.invoke(smoke, level, boss);
                float lost = previousHealth - player.getHealth();
                check(age < ignite ? lost == 0 : lost <= 4.001F, "Smoke damaged early or overlapping fire stacked damage");
                if (age == ignite) check(lost > 0, "Greek fire did not damage a player inside the ignited cloud");
            }
            check(clouds.isEmpty(), "Expired smoke hazards remained active");
            Asterion.LOGGER.info("PASS: smoke warning delay, Greek fire contact damage, overlapping-cloud cap and expiry");

            var attackTick = method(MinotaurEntity.class, "tickBossAttack", ServerLevel.class, ServerPlayer.class);
            var finish = method(MinotaurEntity.class, "finishBossAttack", int.class);
            for (String attack : new String[]{"charge", "horn_ram", "stampede", "smoke_belch"}) {
                player.setPos(20, 121, 0); player.setDeltaMovement(Vec3.ZERO); boss.setYRot(0); boss.yBodyRot = 0;
                check(boss.forceDebugAttack(player, attack), "New/charge attack unavailable: " + attack);
                for (int i = 0; i < 20; i++) { attackTick.invoke(boss, level, player); boss.getLookControl().tick(); }
                check(Vec3.directionFromRotation(0, boss.yBodyRot).dot(new Vec3(1, 0, 0)) > .98,
                        "Attack faces away from its direction: " + attack + " yaw=" + boss.yBodyRot
                                + " direction=" + field(boss, "bossChargeDirection") + " boss=" + boss.position()
                                + " player=" + player.position() + " status=" + boss.debugStatus());
                finish.invoke(boss, 0);
            }
            Asterion.LOGGER.info("PASS: charge, horn ram, stampede and smoke belch face their target");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        finally {
            boss.discard(); ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(previousTime); player.setPos(previous); player.clearFire();
            player.setDeltaMovement(Vec3.ZERO); player.setHealth(previousHealth); player.setGameMode(previousMode);
        }
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) throws ReflectiveOperationException {
        var method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method;
    }
    private static Object field(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void setField(Object object, String name, Object value) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
