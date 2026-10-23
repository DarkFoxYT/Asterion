package net.krodark.asterion.test;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.WeaponCombatSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

public final class WeaponCombatRegression {
    static volatile boolean passed;
    public static void run(ServerPlayer player) {
        var speed = player.getAttribute(Attributes.ATTACK_SPEED);
        double original = speed.getValue();
        int count = speed.getModifiers().size();
        var previous = player.getMainHandItem();
        boolean wasCreative = player.getAbilities().instabuild;
        try {
            var legacy = new ItemStack(Asterion.SICKENED_TWINBLADES);
            if (legacy.getMaxDamage() != 0) throw new AssertionError("Retired Twinblades still have weapon properties");
            player.setItemInHand(InteractionHand.MAIN_HAND, legacy);
            var hit = WeaponCombatSystem.class.getDeclaredMethod("afterDamage", LivingEntity.class,
                    DamageSource.class, float.class, float.class, boolean.class);
            hit.setAccessible(true);
            var source = player.damageSources().playerAttack(player);
            for (int i = 0; i < 720; i++) hit.invoke(null, player, source, 3F, 3F, false);
            if (speed.getValue() != original || speed.getModifiers().size() != count
                    || WeaponCombatSystem.afterblowDamage(source, 3F, player.level().getGameTime()) != 3F)
                throw new AssertionError("Retired Twinblades still alter combat attributes");
            if (java.util.Arrays.stream(WeaponCombatSystem.class.getDeclaredMethods())
                    .anyMatch(method -> method.getName().equals("updateSpeed") || method.getName().equals("tick")))
                throw new AssertionError("Retired combo tick/modifier code is still present");
            var afterblow = new ItemStack(Asterion.AFTERBLOW);
            player.getAbilities().instabuild = false;
            player.setItemInHand(InteractionHand.MAIN_HAND, afterblow);
            player.startUsingItem(InteractionHand.MAIN_HAND);
            long now = player.level().getGameTime();
            if (!net.krodark.asterion.item.AfterblowItem.tryBlock(player, source, 6F)
                    || afterblow.getDamageValue() != 6
                    || net.krodark.asterion.item.AfterblowItem.storedAt(afterblow, now) != 6F
                    || WeaponCombatSystem.afterblowDamage(source, 3F, now) != 9F
                    || WeaponCombatSystem.afterblowDamage(source, 3F, now) != 3F)
                throw new AssertionError("Afterblow must parry, store damage and release it exactly once");
            Asterion.LOGGER.info("ASTERION_AFTERBLOW PASS: parry, durability, stored damage and single-use bonus retained");
            passed = true;
            Asterion.LOGGER.info("ASTERION_WEAPON_COMBAT PASS: 720 legacy-item hits, no modifier changes, no combo tick system");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        finally {
            player.getAbilities().instabuild = wasCreative;
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }
}
