package net.krodark.asterion.item;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Item whose material stack is visualized by the forged tooltip component. */
public class ForgedComponentItem extends Item {
    public ForgedComponentItem(Properties properties) {
        super(properties);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        CompoundTag tag = data.copyTag();
        String sequence = tag.getStringOr("metal_sequence", "");
        if (sequence.isEmpty() || sequence.chars().anyMatch(value -> value < '0' || value > '8'))
            return Optional.empty();
        return Optional.of(new ForgedTooltipComponent(sequence));
    }
}
