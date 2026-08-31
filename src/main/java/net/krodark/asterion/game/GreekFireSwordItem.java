package net.krodark.asterion.game;

import net.krodark.asterion.effect.GreekFireBurn;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class GreekFireSwordItem extends Item {
    public GreekFireSwordItem(Properties properties) { super(properties.sword(net.minecraft.world.item.ToolMaterial.DIAMOND, 3, -2.4F)); }
    @Override public void hurtEnemy(ItemStack stack, LivingEntity victim, LivingEntity attacker) {
        super.hurtEnemy(stack, victim, attacker);
        if (!victim.level().isClientSide()) GreekFireBurn.ignite(victim, 5);
    }
}
