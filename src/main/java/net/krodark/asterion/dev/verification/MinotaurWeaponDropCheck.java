package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurAxeEntity;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

final class MinotaurWeaponDropCheck {
    static void run(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(25, 179, -15, 55, 179, 15))
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 18);
        var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
        boss.setPos(40, 180, 0);
        AABB area = new AABB(20, 179, -20, 60, 205, 20);
        try {
            var drop = MinotaurEntity.class.getDeclaredMethod("dropDeathWeapons", ServerLevel.class);
            drop.setAccessible(true);
            drop.invoke(boss, level);
            var weapons = level.getEntitiesOfClass(MinotaurAxeEntity.class, area);
            check(weapons.size() == 3, "Expected one axe and two swords");
            check(weapons.stream().filter(MinotaurAxeEntity::isSword).count() == 2, "Sword models were not selected");
            double initialHeight = weapons.stream().mapToDouble(MinotaurAxeEntity::getY).sum();
            for (int tick = 0; tick < 300; tick++) for (var weapon : weapons) weapon.tick();
            check(weapons.stream().mapToDouble(MinotaurAxeEntity::getY).sum() < initialHeight, "Weapons did not fall");
            for (var weapon : weapons) {
                check(weapon.getY() >= 180 && weapon.getY() < 183, "Weapon fell through the floor");
                check(weapon.sleeping(), "Weapon did not settle");
                var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
                weapon.saveWithoutId(saved);
                var restored = new MinotaurAxeEntity(Asterion.MINOTAUR_AXE, level);
                restored.load(net.minecraft.world.level.storage.TagValueInput.create(
                        net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
                check(restored.isSword() == weapon.isSword() && restored.sleeping(), "Saved weapon lost its model or rest state");
                check(saved.buildResult().getBooleanOr("harmless", false), "Dropped weapon can still damage players");
            }
            drop.invoke(boss, level);
            check(level.getEntitiesOfClass(MinotaurAxeEntity.class, area).size() == 3, "Corpse duplicated weapons");
            var corpseSave = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                    net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
            boss.saveWithoutId(corpseSave);
            var restoredBoss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
            restoredBoss.load(net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), corpseSave.buildResult()));
            drop.invoke(restoredBoss, level);
            check(level.getEntitiesOfClass(MinotaurAxeEntity.class, area).size() == 3, "Reloaded corpse duplicated weapons");
            weapons.forEach(MinotaurAxeEntity::discard);
            var secondBoss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
            secondBoss.setPos(40, 180, 0);
            var thrown = new MinotaurAxeEntity(Asterion.MINOTAUR_AXE, level);
            thrown.launch(new net.minecraft.world.phys.Vec3(40, 184, 0), net.minecraft.world.phys.Vec3.ZERO, 0);
            level.addFreshEntity(thrown);
            var axeId = MinotaurEntity.class.getDeclaredField("thrownAxe");
            axeId.setAccessible(true);
            axeId.set(secondBoss, thrown.getUUID());
            drop.invoke(secondBoss, level);
            var recovered = level.getEntitiesOfClass(MinotaurAxeEntity.class, area);
            check(recovered.size() == 3 && recovered.contains(thrown), "Already-thrown axe was duplicated or replaced");
            recovered.forEach(MinotaurAxeEntity::discard);
            Asterion.LOGGER.info("PASS: two swords and one axe fall, collide, settle and drop only once");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
