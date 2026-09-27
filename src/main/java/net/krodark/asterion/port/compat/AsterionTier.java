package net.krodark.asterion.port.compat;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/** Simple data holder matching the pre-ToolMaterial Tier API in 1.21.1. */
public record AsterionTier(TagKey<Block> incorrectBlocksForDrops, int uses, float speed,
                           float attackDamageBonus, int enchantmentValue,
                           TagKey<Item> repairItems) implements Tier {
    //? if <1.20.5 {
    /*@Override public int getLevel() { return 4; }*/
    //?}
    @Override public int getUses() { return uses; }
    @Override public float getSpeed() { return speed; }
    @Override public float getAttackDamageBonus() { return attackDamageBonus; }
    public TagKey<Block> getIncorrectBlocksForDrops() { return incorrectBlocksForDrops; }
    @Override public int getEnchantmentValue() { return enchantmentValue; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(repairItems); }
}
