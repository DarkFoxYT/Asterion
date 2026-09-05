package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.AncientContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;

final class AncientContentCheck {
    static void run(MinecraftServer server) {
        var level = server.overworld();
        level.getChunkAt(new BlockPos(0, 200, 0));
        var variants = new java.util.HashSet<Float>();
        boolean armored = false, bare = false;
        for (int i = 0; i < 48; i++) {
            var skeleton = AncientContent.SKELETON.create(level, EntitySpawnReason.COMMAND);
            skeleton.setPos(0, 200, 0);
            skeleton.finalizeSpawn(level, level.getCurrentDifficultyAt(new BlockPos(0,200,0)), EntitySpawnReason.COMMAND, null);
            check(skeleton.getMaxHealth() == 36 && !skeleton.canPickUpLoot(), "Ancient skeleton attributes/pickup incorrect");
            check(skeleton.getMainHandItem().is(Asterion.CELESTIAL_BRONZE_SWORD), "Skeleton equipped vanilla weapon");
            variants.add(skeleton.getMainHandItem().get(DataComponents.CUSTOM_MODEL_DATA).floats().getFirst());
            boolean armor = !skeleton.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || !skeleton.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
            armored |= armor; bare |= !armor;
            if (i == 0) {
                skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                skeleton.setDropChance(EquipmentSlot.CHEST, 2);
                level.addFreshEntity(skeleton);
                var rule = net.minecraft.world.level.gamerules.GameRules.MOB_DROPS;
                boolean previous = level.getGameRules().get(rule);
                try {
                    level.getGameRules().set(rule, true, server);
                    skeleton.hurtServer(level, level.damageSources().genericKill(), 1000);
                } finally { level.getGameRules().set(rule, previous, server); }
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(-5,195,-5,5,205,5));
                check(!drops.isEmpty() && drops.stream().allMatch(e -> e.getItem().is(Items.BONE) || e.getItem().is(AncientContent.ANCIENT_BONE)),
                        "Skeleton death loot: " + drops.stream().map(e -> e.getItem().toString()).toList() + ", alive=" + skeleton.isAlive());
                drops.forEach(ItemEntity::discard);
            }
            skeleton.discard();
        }
        check(variants.size() == 2 && armored && bare, "Missing sword/armor variation");
        Asterion.LOGGER.info("PASS: Ancient Skeleton has 36 health, two bronze swords, occasional armor, no pickup and bones-only death loot even with guaranteed gear drops");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
