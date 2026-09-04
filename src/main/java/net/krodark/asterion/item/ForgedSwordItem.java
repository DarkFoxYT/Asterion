package net.krodark.asterion.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;

/** Marker item for player-assembled, component-driven forged swords. */
public final class ForgedSwordItem extends ForgedComponentItem {
    public ForgedSwordItem(Properties properties) { super(properties); }
    @Override public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }
}
