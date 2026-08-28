package net.krodark.asterion;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public final class AntikytheraBlueprintItem extends Item {
    public AntikytheraBlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.asterion.antikythera_blueprint.line_one"));
        tooltip.accept(Component.translatable("tooltip.asterion.antikythera_blueprint.line_two"));
    }
}
