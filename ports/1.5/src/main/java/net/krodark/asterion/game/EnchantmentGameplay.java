package net.krodark.asterion.game;

import net.krodark.asterion.game.AsterionEnchantments.Kind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative damage, armor decay and conserved durability transfer. */
public final class EnchantmentGameplay {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private EnchantmentGameplay() { }
    private static LivingEntity meleeAttacker(DamageSource source) {
        return (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK))
                && source.getEntity() instanceof LivingEntity entity && source.getDirectEntity() == entity ? entity : null;
    }
    public static float minosBonus(LivingEntity victim, DamageSource source, float damage) {
        LivingEntity attacker = meleeAttacker(source);
        if (attacker == null || damage <= 0 || !Float.isFinite(damage)) return 0;
        int level = AsterionEnchantments.level(attacker.getMainHandItem(), Kind.MINOS_KILLER, attacker);
        return EnchantmentBalance.minosBonus(level, victim.getBbWidth(), victim.getBbHeight(),
                victim instanceof net.krodark.asterion.entity.MinotaurEntity);
    }
    public static void afterDamage(LivingEntity victim, DamageSource source, float damageTaken, boolean blocked) {
        LivingEntity attacker = meleeAttacker(source);
        if (attacker == null || attacker == victim || blocked || damageTaken <= 0
                || !(victim.level() instanceof ServerLevel) || !attacker.isAlive()) return;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack worn = attacker.getItemBySlot(slot);
            if (!worn.isDamageableItem() || worn.getDamageValue() <= 0) continue;
            int level = AsterionEnchantments.level(worn, Kind.DURABILITY_STEAL, attacker);
            if (level <= 0) continue;
            EquipmentSlot donorSlot = donorSlot(victim, slot);
            if (donorSlot == null) continue;
            ItemStack donor = victim.getItemBySlot(donorSlot);
            int transfer = EnchantmentBalance.stolenDurability(level, worn.getDamageValue(),
                    donor.getMaxDamage() - donor.getDamageValue());
            if (transfer == 0) continue;
            damageExactly(victim, donorSlot, transfer);
            worn.setDamageValue(worn.getDamageValue() - transfer);
        }
    }
    private static EquipmentSlot donorSlot(LivingEntity victim, EquipmentSlot preferred) {
        ItemStack matching = victim.getItemBySlot(preferred);
        if (matching.isDamageableItem() && matching.getDamageValue() < matching.getMaxDamage()) return preferred;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = victim.getItemBySlot(slot);
            if (stack.isDamageableItem() && stack.getDamageValue() < stack.getMaxDamage()) return slot;
        }
        return null;
    }
    public static void tickArmor(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel) || !entity.isAlive() || entity.tickCount % 100 != 0
                || entity instanceof Player player && (player.isCreative() || player.isSpectator())) return;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isDamageableItem()) continue;
            int level = AsterionEnchantments.level(stack, Kind.CELESTIAL_CURSE, entity);
            if (level > 0 && entity.tickCount % EnchantmentBalance.curseInterval(level) == 0)
                damageExactly(entity, slot, 1);
        }
    }
    private static void damageExactly(LivingEntity entity, EquipmentSlot slot, int amount) {
        ItemStack stack = entity.getItemBySlot(slot);
        int damage = stack.getDamageValue() + amount;
        if (damage < stack.getMaxDamage()) { stack.setDamageValue(damage); return; }
        net.krodark.asterion.port.compat.EntityCompat.broken(entity, stack.getItem(), slot);
        entity.setItemSlot(slot, ItemStack.EMPTY);
    }
}
