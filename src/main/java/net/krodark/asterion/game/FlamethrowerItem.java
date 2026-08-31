package net.krodark.asterion.game;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public final class FlamethrowerItem extends Item {
    public FlamethrowerItem(Properties properties) { super(properties); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand); return InteractionResult.CONSUME;
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return 72000; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BOW; }
    @Override public void onUseTick(Level world, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(world instanceof ServerLevel level) || remaining % 4 != 0 || entity.isSpectator() || entity.isInWater()) return;
        var direction = entity.getLookAngle();
        GasClouds.emitFlamethrower(level, entity.getEyePosition().add(direction.scale(.6)), direction.scale(.4), entity.getUUID());
        if (remaining % 20 == 0) stack.hurtAndBreak(1, entity, entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
    }
    public static void ignite(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (!held.is(GameplayContent.FLAMETHROWER) || player.isSpectator() || !player.isAlive()
                || player.getCooldowns().isOnCooldown(held)) return;
        player.stopUsingItem();
        GasClouds.ignite(player.level(), player.getEyePosition(), player.getUUID());
        player.getCooldowns().addCooldown(held, 10);
    }
}
