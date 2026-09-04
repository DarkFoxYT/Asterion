package net.krodark.asterion.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/** Assigns a stable purity to a newly smelted/created metal stack on first inventory tick. */
public final class PurityMetalItem extends Item {
    public PurityMetalItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existing == null ? new CompoundTag() : existing.copyTag();
        if (tag.contains("purity")) return;
        int purity = entity instanceof Player player && player.isCreative()
                ? 100 : 55 + entity.getRandom().nextInt(46);
        tag.putInt("purity", purity);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                List.of(purity / 100.0F), List.of(), List.of(), List.of()));
    }
}
