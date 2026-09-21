package net.krodark.asterion.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

 
public final class AfterblowItem extends Item {
    private static final String STORED_DAMAGE = "afterblow_damage";
    private static final String STORED_AT = "afterblow_stored_at";
    private static final int BLOCK_COOLDOWN_TICKS = 40;
    private static final int EXPIRES_TICKS = 100;

    public AfterblowItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, net.minecraft.world.entity.player.Player player,
                                 InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
         
         
        if (player.getCooldowns().isOnCooldown(stack) || storedAt(stack, level.getGameTime()) > .001F)
            return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return 40; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BLOCK; }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (user instanceof ServerPlayer player)
            player.getCooldowns().addCooldown(stack, BLOCK_COOLDOWN_TICKS);
        return false;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (user instanceof ServerPlayer player)
            player.getCooldowns().addCooldown(stack, BLOCK_COOLDOWN_TICKS);
        return stack;
    }

    public static boolean tryBlock(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float damage) {
        if (!player.isUsingItem() || player.getTicksUsingItem() >= 40 || !Float.isFinite(damage) || damage <= 0
                || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)
                || source.getDirectEntity() == null) return false;
        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof AfterblowItem) || player.getCooldowns().isOnCooldown(stack)
                || storedAt(stack, player.level().getGameTime()) > .001F) return false;

        long now = player.level().getGameTime();
        writeStored(stack, damage, now);
        player.getCooldowns().addCooldown(stack, BLOCK_COOLDOWN_TICKS);
        int durability = Math.max(1, (int)Math.ceil(damage));
        InteractionHand hand = player.getUsedItemHand();
        player.stopUsingItem();
        stack.hurtAndBreak(durability, player, hand);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.krodark.asterion.Asterion.AFTERBLOW_PARRY,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 1.0F);
        return true;
    }

     
    public static float consumeStored(ItemStack stack, long now) {
        float stored = storedAt(stack, now);
         
         
        if (rawStored(stack) > 0) writeStored(stack, 0, now);
        return stored;
    }

    public static float storedAt(ItemStack stack, long now) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag tag = data.copyTag();
        float raw = tag.getFloatOr(STORED_DAMAGE, 0);
        float value = Float.isFinite(raw) ? Math.max(0, raw) : 0;
        long elapsed = Math.max(0, now - tag.getLongOr(STORED_AT, now));
        if (elapsed >= EXPIRES_TICKS) return 0;
        return value;
    }

    private static void writeStored(ItemStack stack, float value, long now) {
        CustomData old = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = old == null ? new CompoundTag() : old.copyTag();
        if (value <= .001F) {
            tag.remove(STORED_DAMAGE);
            tag.remove(STORED_AT);
        } else {
            tag.putFloat(STORED_DAMAGE, value);
            tag.putLong(STORED_AT, now);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        updateModel(stack, value > .001F);
    }

    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.EquipmentSlot slot) {
        float stored = storedAt(stack, level.getGameTime());
        if (stored <= .001F && rawStored(stack) > 0) writeStored(stack, 0, level.getGameTime());
        else updateModel(stack, stored > .001F);
    }

    private static float rawStored(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        float value = data.copyTag().getFloatOr(STORED_DAMAGE, 0);
        return Float.isFinite(value) ? Math.max(0, value) : 0;
    }

    private static void updateModel(ItemStack stack, boolean powered) {
        var model = new net.minecraft.world.item.component.CustomModelData(
                java.util.List.of(), java.util.List.of(powered), java.util.List.of(), java.util.List.of());
        if (!model.equals(stack.get(DataComponents.CUSTOM_MODEL_DATA))) stack.set(DataComponents.CUSTOM_MODEL_DATA, model);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        float stored = rawStored(stack);
        if (stored > .01F)
            tooltip.accept(Component.translatable("tooltip.asterion.afterblow.stored", stored)
                    .withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.asterion.afterblow.guard.1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.asterion.afterblow.guard.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.asterion.afterblow.guard.3").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.asterion.afterblow.guard.4").withStyle(ChatFormatting.DARK_GRAY));
    }
}
