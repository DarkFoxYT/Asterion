package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.network.ragdoll.RagdollServerNetworking;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Set;

/** Samples the real selector in the arena, including conditional counters and drawn-weapon preference. */
final class MinotaurCombatSelectionCheck {
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void run(ServerLevel level, ServerPlayer player) {
        Vec3 previous = player.position();
        var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
        boss.setPos(0, 37, 0); boss.setYRot(0); boss.setYHeadRot(0);
        boss.getRandom().setSeed(917420);
        try {
            var begin = MinotaurEntity.class.getDeclaredMethod("beginBossIntercept", ServerPlayer.class);
            begin.setAccessible(true); begin.invoke(boss, player);
            var choose = MinotaurEntity.class.getDeclaredMethod("chooseCombatAttack", ServerPlayer.class, double.class);
            choose.setAccessible(true);
            var stage = MinotaurEntity.class.getDeclaredField("bossStage"); stage.setAccessible(true);
            var weapon = MinotaurEntity.class.getDeclaredField("DATA_WEAPON"); weapon.setAccessible(true);
            var weaponKey = (EntityDataAccessor<Integer>)weapon.get(null);
            Set<String> expected = new HashSet<>(MinotaurEntity.debugAttackNames());
            expected.remove("retrieve_axe"); // Pickup is exercised using the actual thrown object in the game test.
            expected.remove("rage_roar"); // Triggered once by max rage, never randomly selected.
            for (String phase : new String[]{"PILLARS", "EXTREME"}) {
                stage.set(boss, Enum.valueOf((Class)stage.getType(), phase));
                Set<String> seen = new HashSet<>();
                for (int scene = 0; scene < 10; scene++) {
                    double distance = new double[]{4, 4, 7, 12, 20, 28, 3, 4, 18, 20}[scene];
                    player.setPos(0, scene == 6 ? 39 : 37, scene == 1 ? -distance : distance);
                    field(boss, "closeBurstDamage", scene == 7 ? 20F : 0F);
                    field(boss, "closeBurstTicks", scene == 7 ? 40 : 0);
                    field(boss, "storedArrows", scene == 9 ? 4 : 0);
                    if (scene == 8) RagdollServerNetworking.markRagdolled(player, 20);
                    for (int roll = 0; roll < 250; roll++) {
                        boss.getEntityData().set(weaponKey, 0);
                        seen.add(choose.invoke(boss, player, boss.distanceTo(player)).toString().toLowerCase(java.util.Locale.ROOT));
                    }
                }
                Set<String> missing = new HashSet<>(expected); missing.removeAll(seen);
                check(missing.isEmpty(), phase + " never selected attacks: " + missing);
                check(!seen.contains("wall_shove") && !seen.contains("red_lightning_charge")
                        && !seen.contains("arena_sweep") && !seen.contains("spin_combo"), "Removed attack selected");
                Asterion.LOGGER.info("PASS: {} arena selected every contextual combat attack: {}", phase, seen);
            }
            field(boss, "storedArrows", 0); field(boss, "closeBurstDamage", 0F); field(boss, "closeBurstTicks", 0);
            player.setPos(0, 37, 6.5);
            for (int mode : new int[]{1, 2}) {
                boss.getEntityData().set(weaponKey, mode); field(boss, "weaponUsesRemaining", 3);
                int kept = 0;
                for (int roll = 0; roll < 300; roll++) {
                    String selected = choose.invoke(boss, player, boss.distanceTo(player)).toString();
                    if (mode == 1 ? Set.of("CLEAVE", "AXE_CHOP", "SLAM").contains(selected)
                            : selected.equals("SWORD_COMBO")) kept++;
                }
                check(kept > 210, "Drawn weapon was discarded too readily: " + mode + " / " + kept);
                Asterion.LOGGER.info("PASS: weapon mode {} retained for {}/300 tactical choices", mode, kept);
            }
            player.setPos(0, 37, 20); player.setDeltaMovement(Vec3.ZERO);
            boss.getEntityData().set(weaponKey, 1); field(boss, "weaponUsesRemaining", 3);
            int throwsChosen = 0;
            for (int roll = 0; roll < 300; roll++)
                if (choose.invoke(boss, player, boss.distanceTo(player)).toString().equals("AXE_THROW")) throwsChosen++;
            check(throwsChosen >= 25, "A drawn axe was effectively forbidden from being thrown: " + throwsChosen);
            var axeOut = MinotaurEntity.class.getDeclaredField("DATA_AXE_OUT"); axeOut.setAccessible(true);
            boss.getEntityData().set((EntityDataAccessor<Boolean>)axeOut.get(null), true);
            player.setPos(0, 37, 6.5); boss.getEntityData().set(weaponKey, 2);
            int swordsChosen = 0;
            for (int roll = 0; roll < 150; roll++) {
                String attack = choose.invoke(boss, player, boss.distanceTo(player)).toString();
                check(!Set.of("AXE_THROW", "AXE_CHOP", "CLEAVE", "SLAM").contains(attack), "Missing axe reused: " + attack);
                check(!attack.equals("SPIN_COMBO"), "Removed sword spin selected");
                if (attack.equals("SWORD_COMBO")) swordsChosen++;
            }
            check(swordsChosen >= 100, "Swords were not used while the axe was away");
            Asterion.LOGGER.info("PASS: drawn axe thrown in {}/300 ranged choices; swords used {}/150 times with axe in world", throwsChosen, swordsChosen);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        finally { player.setPos(previous); boss.discard(); }
    }
    private static void field(MinotaurEntity boss, String name, Object value) throws ReflectiveOperationException {
        var field = MinotaurEntity.class.getDeclaredField(name); field.setAccessible(true); field.set(boss, value);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
