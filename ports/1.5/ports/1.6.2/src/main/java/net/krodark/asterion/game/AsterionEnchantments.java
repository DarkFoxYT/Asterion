package net.krodark.asterion.game;

import net.krodark.asterion.Asterion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;


public final class AsterionEnchantments {
    public enum Kind {
        SWIFTY_WATER("swifty_water", 3), MINOS_KILLER("minos_killer", 3),
        CELESTIAL_CURSE("celestial_curse", 2), DURABILITY_STEAL("durability_steal", 5);
        public final String path;
        public final int max;
        public final ResourceKey<Enchantment> key;
        private Enchantment legacy;
        Kind(String path, int max) {
            this.path = path; this.max = max;
            key = ResourceKey.create(Registries.ENCHANTMENT, Asterion.id(path));
        }
    }
    private static boolean initialized;
    private AsterionEnchantments() { }
    public static void initialize() {
        if (initialized) return;
        initialized = true;
    }
    public static int level(ItemStack stack, Kind kind, LivingEntity owner) {
        if (stack.isEmpty()) return 0;
        var holder = owner.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(kind.key);
        return holder.isEmpty() ? 0 : Math.min(kind.max, EnchantmentHelper.getItemEnchantmentLevel(holder.get(), stack));
    }
    public static void enchant(ItemStack stack, Kind kind, int level, LivingEntity owner) {
        level = Math.max(1, Math.min(kind.max, level));
        var holder = owner.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(kind.key).orElseThrow();
        stack.enchant(holder, level);
    }
    public static int swifty(LivingEntity entity) {
        return level(entity.getItemBySlot(EquipmentSlot.FEET), Kind.SWIFTY_WATER, entity);
    }
}
