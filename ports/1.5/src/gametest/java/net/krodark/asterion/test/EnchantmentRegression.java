package net.krodark.asterion.test;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.fluid.HeavyWater;
import net.krodark.asterion.fluid.HeavyWaterFatigue;
import net.krodark.asterion.game.AsterionEnchantments;
import net.krodark.asterion.game.AsterionEnchantments.Kind;
import net.krodark.asterion.game.EnchantmentGameplay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Opt-in gameplay tests in a disposable world; never packaged in the release. */
public final class EnchantmentRegression {
    public static volatile boolean passed;
    public static volatile Throwable failure;
    public static void run(ServerPlayer player) {
        try {
            int[][] routes = {{0,0},{1024,13},{5120,14},{13312,1},{15360,2},{15361,3},
                    {8,12},{9,12},{2,4},{4,5},{12,6},{28,7},{60,8},{64,9},{128,10},{256,11},{512,15}};
            for (var route : routes) {
                var saved = new com.google.gson.JsonObject(); saved.addProperty("milestones", route[0]);
                var progress = net.krodark.asterion.game.SharedObjectiveProgress.CODEC
                        .parse(com.mojang.serialization.JsonOps.INSTANCE, saved).result().orElseThrow();
                require(progress.stage() == route[1], "Incorrect saved objective route: " + route[0]);
                if (route[1] == 4 || route[1] == 9 || route[1] == 10 || route[1] == 12)
                    require(progress.navigationTarget(Vec3.ZERO) != null, "Missing boss/key navigation target");
            }
            var level = (net.minecraft.server.level.ServerLevel)player.level();
            var wearer = new ArmorStand(level, 0, 300, 0);
            var donor = new ArmorStand(level, 2, 300, 0);
            var attack = wearer.damageSources().mobAttack(wearer);
            for (int enchantmentLevel = 1; enchantmentLevel <= 5; enchantmentLevel++) {
                var helmet = new ItemStack(Items.IRON_HELMET);
                helmet.setDamageValue(20);
                AsterionEnchantments.enchant(helmet, Kind.DURABILITY_STEAL, enchantmentLevel, wearer);
                wearer.setItemSlot(EquipmentSlot.HEAD, helmet);
                var target = new ItemStack(Items.IRON_HELMET);
                target.setDamageValue(10);
                donor.setItemSlot(EquipmentSlot.HEAD, target);
                EnchantmentGameplay.afterDamage(donor, attack, 2, false);
                require(helmet.getDamageValue() == 20 - enchantmentLevel
                        && target.getDamageValue() == 10 + enchantmentLevel, "Durability was not conserved at level " + enchantmentLevel);
            }
            var recipient = wearer.getItemBySlot(EquipmentSlot.HEAD);
            recipient.setDamageValue(20);
            donor.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(donor.getItemBySlot(EquipmentSlot.HEAD).getMaxDamage() - 2);
            EnchantmentGameplay.afterDamage(donor, attack, 2, false);
            require(recipient.getDamageValue() == 18 && donor.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Donor break duplicated durability");
            EnchantmentGameplay.afterDamage(donor, attack, 2, false);
            require(recipient.getDamageValue() == 18, "Unarmored target repaired armor");
            donor.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            EnchantmentGameplay.afterDamage(donor, attack, 2, true);
            EnchantmentGameplay.afterDamage(donor, attack, 0, false);
            require(recipient.getDamageValue() == 18, "Blocked or zero damage stole durability");

            for (int curseLevel = 1; curseLevel <= 2; curseLevel++) {
                var chest = new ItemStack(Items.IRON_CHESTPLATE);
                AsterionEnchantments.enchant(chest, Kind.CELESTIAL_CURSE, curseLevel, donor);
                donor.setItemSlot(EquipmentSlot.CHEST, chest);
                donor.tickCount = 100;
                EnchantmentGameplay.tickArmor(donor);
                require(chest.getDamageValue() == (curseLevel == 1 ? 0 : 1), "Curse rate at five seconds");
                donor.tickCount = 200;
                EnchantmentGameplay.tickArmor(donor);
                require(chest.getDamageValue() == curseLevel, "Curse II must drain twice as fast");
                donor.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                donor.tickCount = 400;
                EnchantmentGameplay.tickArmor(donor);
                require(chest.getDamageValue() == curseLevel, "Unequipped cursed armor decayed");
            }

            var sword = new ItemStack(Items.DIAMOND_SWORD);
            wearer.setItemInHand(InteractionHand.MAIN_HAND, sword);
            AsterionEnchantments.enchant(sword, Kind.MINOS_KILLER, 3, wearer);
            var minotaur = new net.krodark.asterion.entity.MinotaurEntity(Asterion.MINOTAUR, level);
            float small = EnchantmentGameplay.minosBonus(donor, attack, 4);
            float boss = EnchantmentGameplay.minosBonus(minotaur, attack, 4);
            require(small > 0 && small < 3 && boss > 12.5 && boss > small, "Minos Killer size scaling or Minotaur bonus");

            player.getAbilities().flying = false;
            player.setSprinting(false);
            player.setNoGravity(true);
            for (boolean heavy : new boolean[]{false, true}) {
                for (int x = -1; x <= 1; x++) for (int y = 300; y <= 303; y++) for (int z = -1; z <= 1; z++)
                    level.setBlock(new BlockPos(x, y, z), heavy ? HeavyWater.WATER_BLOCK.defaultBlockState() : Blocks.WATER.defaultBlockState(), 2);
                player.setPos(.5, 301, .5);
                player.setDeltaMovement(Vec3.ZERO);
                player.setOnGround(false);
                player.baseTick();
                require(heavy ? HeavyWaterFatigue.swimmingInHeavyWater(player) : player.isInWater(),
                        "Swimming fixture: heavy=" + heavy + ", inWater=" + player.isInWater()
                                + ", fluid=" + level.getFluidState(player.blockPosition()).getType());
                player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                float baseline = player.getSpeed();
                float previous = baseline;
                for (int swifty = 1; swifty <= 3; swifty++) {
                    var boots = new ItemStack(Items.DIAMOND_BOOTS);
                    AsterionEnchantments.enchant(boots, Kind.SWIFTY_WATER, swifty, player);
                    player.setItemSlot(EquipmentSlot.FEET, boots);
                    float speed = player.getSpeed();
                    require(speed > previous && speed > baseline, "Swifty Water did not increase real player swimming speed");
                    previous = speed;
                }
                require(previous > baseline * 2, "Swifty Water III lost its speed bonus");
            }
            for (int x = -1; x <= 1; x++) for (int y = 301; y <= 303; y++) for (int z = -1; z <= 1; z++)
                level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            player.setPos(.5, 300.2, .5);
            player.setOnGround(true);
            player.baseTick();
            require(HeavyWaterFatigue.touchingHeavyWater(player) && !HeavyWaterFatigue.swimmingInHeavyWater(player),
                    "Shallow heavy water should count as wading, not deep-water fatigue");
            var swimmingBoots = player.getItemBySlot(EquipmentSlot.FEET);
            float wadingSpeed = player.getSpeed();
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            require(wadingSpeed > player.getSpeed(), "Swifty Water did not help in shallow heavy water");
            player.setItemSlot(EquipmentSlot.FEET, swimmingBoots);
            player.setPos(10.5, 305, 10.5);
            player.baseTick();
            float land = player.getSpeed();
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            require(Math.abs(player.getSpeed() - land) < .00001, "Swifty Water changed land speed");

            BlockPos chestPos = new BlockPos(20, 300, 20);
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
            Container container = (Container)level.getBlockEntity(chestPos);
            var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
            int levelsFound = 0, rolls = 0;
            while (levelsFound != 3 && rolls++ < 2048) {
                container.clearContent();
                level.getServer().getCommands().performPrefixedCommand(source,
                        "loot replace block 20 300 20 container.0 loot asterion:chests/catacomb_cache");
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack item = container.getItem(slot);
                    int curse = AsterionEnchantments.level(item, Kind.CELESTIAL_CURSE, player);
                    if (curse > 0) {
                        String name = BuiltInRegistries.ITEM.getKey(item.getItem()).getPath();
                        require(name.endsWith("_helmet") || name.endsWith("_chestplate") || name.endsWith("_leggings") || name.endsWith("_boots"),
                                "Catacombs cursed an item that is not armor");
                        levelsFound |= 1 << (curse - 1);
                    }
                }
            }
            require(levelsFound == 3, "Catacombs loot did not generate both curse levels");
            var queen = new net.krodark.asterion.entity.QueenBeetleEntity(Asterion.QUEEN_BEETLE, level);
            queen.setPos(player.getX(), player.getY(), player.getZ() + 2);
            level.addFreshEntity(queen);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.getInventory().add(new ItemStack(Asterion.TAINTED_PETALS, 32));
            queen.interact(player, InteractionHand.MAIN_HAND);
            queen.interact(player, InteractionHand.MAIN_HAND);
            require(net.krodark.asterion.entity.QueenBeetleEntity.questIndex(player) == 1,
                    "Queen's first request did not complete");
            Asterion.LOGGER.info("ASTERION_ENCHANTMENTS PASS: saved objective routes/navigation, four registries, water/heavy-water speed, size damage, curse timing, durability conservation/breaks and real Catacombs loot ({} rolls)", rolls);
            passed = true;
        } catch (Throwable thrown) { failure = thrown; }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
