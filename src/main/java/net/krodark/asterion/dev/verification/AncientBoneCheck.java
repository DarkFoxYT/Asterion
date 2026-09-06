package net.krodark.asterion.dev.verification;

import java.util.ArrayList;
import java.util.Set;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.krodark.asterion.game.AncientContent;
import net.krodark.asterion.network.CrucibleControlPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.*;

final class AncientBoneCheck {
    static void run(MinecraftServer server) {
        var level = server.overworld();
        var player = server.getPlayerList().getPlayers().getFirst();
        var oldLevel = player.level();
        var oldPosition = player.position();
        var inventory = new ArrayList<ItemStack>();
        for (int slot = 0; slot < 36; slot++) { inventory.add(player.getInventory().getItem(slot).copy()); player.getInventory().setItem(slot, ItemStack.EMPTY); }
        BlockPos pos = new BlockPos(0, 200, 0);
        try {
            player.teleportTo(level, .5, 200, 2.5, Set.of(), 0, 0, true);
            var heat = CrucibleBlockEntity.class.getDeclaredField("temperature"); heat.setAccessible(true);
            for (boolean boneRecipe : new boolean[]{false, true}) for (boolean reverse : new boolean[]{false, true}) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                level.setBlock(pos, Asterion.CRUCIBLE.defaultBlockState(), 18);
                level.setBlock(pos.below(), Blocks.CAMPFIRE.defaultBlockState(), 3);
                var forge = (CrucibleBlockEntity)level.getBlockEntity(pos);
                var ingredients = new ArrayList<Item>(boneRecipe
                        ? java.util.List.of(Asterion.CELESTIAL_STEEL_INGOT, Items.IRON_INGOT,
                            AncientContent.ANCIENT_BONE, AncientContent.ANCIENT_BONE, AncientContent.ANCIENT_BONE)
                        : java.util.List.of(Items.IRON_INGOT, Items.IRON_INGOT, Items.COAL, Items.COAL));
                if (reverse) java.util.Collections.reverse(ingredients);
                Item output = boneRecipe ? Asterion.BONESTEEL_INGOT : Asterion.CELESTIAL_STEEL_INGOT;
                int target = boneRecipe ? 900 : 700;
                check(forge.insert(player, new ItemStack(Asterion.INGOT_CAST)), "Cast rejected");
                for (Item ingredient : ingredients)
                    check(forge.insert(player, new ItemStack(ingredient)), "Ingredient rejected: " + ingredient);
                check(forge.hasUnsmeltedIngredients(), "Ingredients reacted on insertion");
                var saved = forge.saveWithFullMetadata(level.registryAccess());
                forge = (CrucibleBlockEntity)BlockEntity.loadStatic(pos, forge.getBlockState(), saved, level.registryAccess());
                level.setBlockEntity(forge);
                check(forge.materialUnits() == ingredients.size(), "Ingredients lost on reload");
                for (int tick = 0; tick < 250; tick++) {
                    heat.setInt(forge, target);
                    CrucibleBlockEntity.tick(level, pos, forge.getBlockState(), forge);
                }
                check(dropped(level, pos, output) == 0 && forge.hasUnsmeltedIngredients(), "Automatic smelting");
                for (int wrong : new int[]{target - 9, target + 9}) {
                    heat.setInt(forge, wrong);
                    forge.control(player, CrucibleControlPayload.POUR);
                    check(forge.materialUnits() == ingredients.size(), "Wrong heat consumed ingredients");
                }
                heat.setInt(forge, target);
                forge.control(player, CrucibleControlPayload.POUR);
                check(dropped(level, pos, output) == 1 && forge.materialUnits() == 0, "Recipe failed to cast exactly one ingot");
                forge.control(player, CrucibleControlPayload.POUR);
                check(dropped(level, pos, output) == 1, "Duplicate output");
                clearDropped(level, pos);
            }
            var skeleton = AncientContent.SKELETON.create(level, EntitySpawnReason.COMMAND);
            var params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, skeleton)
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().genericKill()).create(LootContextParamSets.ENTITY);
            var loot = server.reloadableRegistries().getLootTable(skeleton.getLootTable().orElseThrow());
            int rareDrops = 0;
            for (int seed = 1; seed <= 1000; seed++) {
                var drops = new ArrayList<ItemStack>();
                loot.getRandomItems(params, seed, drops::add);
                check(drops.stream().allMatch(s -> s.is(Items.BONE) || s.is(AncientContent.ANCIENT_BONE)), "Skeleton loot included equipment");
                rareDrops += drops.stream().filter(s -> s.is(AncientContent.ANCIENT_BONE)).mapToInt(ItemStack::getCount).sum();
            }
            check(rareDrops >= 1 && rareDrops <= 25, "Ancient Bone is not a rare drop: " + rareDrops + "/1000");
            Asterion.LOGGER.info("PASS: Ancient Bone rare loot ({}/1000), either input order, cold/incomplete mixture rejection, saved inputs, raw refunds, external-heat casting, front ejection and no duplicate output", rareDrops);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        finally {
            for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, inventory.get(slot));
            player.teleportTo(oldLevel, oldPosition.x, oldPosition.y, oldPosition.z, Set.of(), 0, 0, true);
        }
    }
    private static int count(net.minecraft.server.level.ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) if (player.getInventory().getItem(slot).is(item)) count += player.getInventory().getItem(slot).getCount();
        return count;
    }
    private static int dropped(net.minecraft.server.level.ServerLevel level, BlockPos pos, Item item) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(pos).inflate(5), entity -> entity.getItem().is(item)).stream()
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }
    private static void clearDropped(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(5)).forEach(net.minecraft.world.entity.Entity::discard);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
