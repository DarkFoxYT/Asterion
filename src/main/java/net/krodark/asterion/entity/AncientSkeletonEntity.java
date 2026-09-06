package net.krodark.asterion.entity;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

 
public final class AncientSkeletonEntity extends Skeleton {
    public AncientSkeletonEntity(EntityType<? extends Skeleton> type, Level level) { super(type, level); }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder attributes() {
        return createAttributes().add(Attributes.MAX_HEALTH, 36).add(Attributes.MOVEMENT_SPEED, .24)
                .add(Attributes.FOLLOW_RANGE, 24).add(Attributes.ATTACK_DAMAGE, 3);
    }

    @Override protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        ItemStack sword = new ItemStack(Asterion.CELESTIAL_BRONZE_SWORD);
        sword.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(java.util.List.of(random.nextBoolean() ? 1F : 2F),
                        java.util.List.of(), java.util.List.of(), java.util.List.of()));
        setItemSlot(EquipmentSlot.MAINHAND, sword);
        if (random.nextInt(3) == 0) {
            EquipmentSlot slot = random.nextBoolean() ? EquipmentSlot.HEAD : EquipmentSlot.CHEST;
            setItemSlot(slot, new ItemStack(slot == EquipmentSlot.HEAD ? Items.IRON_HELMET : Items.IRON_CHESTPLATE));
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0);
        setCanPickUpLoot(false);
    }

     
    @Override protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source,
                                                 boolean killedByPlayer) { }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason reason, SpawnGroupData group) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, group);
        setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0);
        return result;
    }
    @Override public boolean canFreeze() { return false; }

    public static boolean canSpawn(EntityType<AncientSkeletonEntity> type, ServerLevelAccessor level,
                                   EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason == EntitySpawnReason.SPAWNER)
            return checkMonsterSpawnRules(type, level, reason, pos, random);
        return reason == EntitySpawnReason.NATURAL
                && level.getBiome(pos).is(net.krodark.asterion.Asterion.CATACOMBS_BIOME)
                && random.nextInt(12) == 0
                && checkMonsterSpawnRules(type, level, reason, pos, random);
    }
}
