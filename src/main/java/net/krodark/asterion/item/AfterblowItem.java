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

/** A forty-tick guard that converts blocked damage into one retaliatory strike. */
public final class AfterblowItem extends Item {
    private static final String STORED_DAMAGE = "afterblow_damage";
    private static final String STORED_AT = "afterblow_stored_at";
    private static final int FULL_STRENGTH_TICKS = 100;
    private static final int EXPIRES_TICKS = 200;

    public AfterblowItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, net.minecraft.world.entity.player.Player player,
                                 InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return 40; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BLOCK; }

    public static boolean tryBlock(ServerPlayer player, float damage) {
        if (!player.isUsingItem() || player.getTicksUsingItem() >= 40 || damage <= 0) return false;
        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof AfterblowItem)) return false;

        long now = player.level().getGameTime();
        writeStored(stack, storedAt(stack, now) + damage, now);
        int durability = Math.max(1, (int)Math.ceil(damage));
        InteractionHand hand = player.getUsedItemHand();
        stack.hurtAndBreak(durability, player, hand);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 0.78F + player.getRandom().nextFloat() * .15F);
        return true;
    }

    /** Removes and returns the still-live charge; every successful attack gets one discharge. */
    public static float consumeStored(ItemStack stack, long now) {
        float stored = storedAt(stack, now);
        if (stored > 0) writeStored(stack, 0, now);
        return stored;
    }

    private static float storedAt(ItemStack stack, long now) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag tag = data.copyTag();
        float value = Math.max(0, tag.getFloatOr(STORED_DAMAGE, 0));
        long elapsed = Math.max(0, now - tag.getLongOr(STORED_AT, now));
        if (elapsed <= FULL_STRENGTH_TICKS) return value;
        if (elapsed >= EXPIRES_TICKS) return 0;

        // Normalized exponential falloff: exactly 100% at five seconds and 0% at ten.
        double progress = (elapsed - FULL_STRENGTH_TICKS)
                / (double)(EXPIRES_TICKS - FULL_STRENGTH_TICKS);
        double floor = Math.exp(-4D);
        return (float)(value * (Math.exp(-4D * progress) - floor) / (1D - floor));
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
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        float stored = data == null ? 0 : data.copyTag().getFloatOr(STORED_DAMAGE, 0);
        if (stored > .01F)
            tooltip.accept(Component.translatable("tooltip.asterion.afterblow.stored", stored)
                    .withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.asterion.afterblow.guard")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
