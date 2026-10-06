package net.krodark.asterion.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;

public final class EntityVersionCompatibility {
    private EntityVersionCompatibility() { }
    public static void invulnerability(Entity entity, int ticks) {
        //? if >=26.3 {
        /*entity.setInvulnerableTime(ticks);
        *///?} else {
        entity.invulnerableTime = ticks;
        //?}
    }
    public static void swing(LivingEntity entity, InteractionHand hand, boolean fromServer) {
        //? if >=26.3 {
        /*entity.swing(hand, entity.getItemInHand(hand).getOrDefault(net.minecraft.core.component.DataComponents.ATTACK_ANIMATION,
            net.minecraft.world.item.component.SwingAnimation.DEFAULT), fromServer);
        *///?} else {
        entity.swing(hand, fromServer);
        //?}
    }
    public static net.minecraft.world.entity.item.ItemEntity drop(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.item.ItemStack stack, boolean random) {
        //? if >=26.3 {
        /*return player.drop(stack, random, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        return player.drop(stack, random);
        //?}
    }
}
