package net.krodark.asterion.game;

import net.krodark.asterion.Asterion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Registry bridge for static 1.20.1 and data-driven enchantments in later versions. */
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
        //? if <1.20.5 {
        /*for (Kind kind : Kind.values()) kind.legacy = net.krodark.asterion.port.compat.AsterionRegistry.register(
                net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT, Asterion.id(kind.path), new Legacy(kind));*/
        //?}
    }
    public static int level(ItemStack stack, Kind kind, LivingEntity owner) {
        if (stack.isEmpty()) return 0;
        //? if >=1.20.5 {
        var holder = owner.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(kind.key);
        return holder.isEmpty() ? 0 : Math.min(kind.max, EnchantmentHelper.getItemEnchantmentLevel(holder.get(), stack));
        //?} else {
        /*return kind.legacy == null ? 0 : Math.min(kind.max, EnchantmentHelper.getItemEnchantmentLevel(kind.legacy, stack));*/
        //?}
    }
    public static void enchant(ItemStack stack, Kind kind, int level, LivingEntity owner) {
        level = Math.max(1, Math.min(kind.max, level));
        //? if >=1.20.5 {
        var holder = owner.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(kind.key).orElseThrow();
        stack.enchant(holder, level);
        //?} else {
        /*stack.enchant(kind.legacy, level);*/
        //?}
    }
    public static int swifty(LivingEntity entity) {
        return level(entity.getItemBySlot(EquipmentSlot.FEET), Kind.SWIFTY_WATER, entity);
    }

    //? if <1.20.5 {
    /*private static final class Legacy extends Enchantment {
        private final Kind kind;
        Legacy(Kind kind) {
            super(kind == Kind.CELESTIAL_CURSE ? Rarity.VERY_RARE : Rarity.RARE,
                    kind == Kind.SWIFTY_WATER ? net.minecraft.world.item.enchantment.EnchantmentCategory.ARMOR_FEET
                            : kind == Kind.MINOS_KILLER ? net.minecraft.world.item.enchantment.EnchantmentCategory.WEAPON
                            : net.minecraft.world.item.enchantment.EnchantmentCategory.ARMOR,
                    kind == Kind.SWIFTY_WATER ? new EquipmentSlot[]{EquipmentSlot.FEET}
                            : kind == Kind.MINOS_KILLER ? new EquipmentSlot[]{EquipmentSlot.MAINHAND}
                            : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET});
            this.kind = kind;
        }
        @Override public int getMaxLevel() { return kind.max; }
        @Override public int getMinCost(int level) {
            return kind == Kind.CELESTIAL_CURSE ? 25 : kind == Kind.DURABILITY_STEAL ? 8 + (level - 1) * 6
                    : (kind == Kind.MINOS_KILLER ? 10 : 12) + (level - 1) * 10;
        }
        @Override public int getMaxCost(int level) { return getMinCost(level) + 25; }
        @Override public boolean isCurse() { return kind == Kind.CELESTIAL_CURSE; }
        @Override public boolean isTreasureOnly() { return isCurse(); }
        @Override public boolean canEnchant(ItemStack stack) {
            if (kind == Kind.MINOS_KILLER) return stack.is(net.minecraft.tags.ItemTags.SWORDS)
                    || stack.getItem() instanceof net.minecraft.world.item.SwordItem
                    || stack.getItem() instanceof net.minecraft.world.item.AxeItem;
            return super.canEnchant(stack);
        }
        @Override protected boolean checkCompatibility(Enchantment other) {
            if (kind == Kind.SWIFTY_WATER && (other == net.minecraft.world.item.enchantment.Enchantments.DEPTH_STRIDER
                    || other == net.minecraft.world.item.enchantment.Enchantments.FROST_WALKER)) return false;
            if (kind == Kind.MINOS_KILLER && other instanceof net.minecraft.world.item.enchantment.DamageEnchantment) return false;
            return super.checkCompatibility(other);
        }
    }*/
    //?}
}
