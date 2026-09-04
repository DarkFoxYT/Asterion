package net.krodark.asterion.item;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Material order used to compose the authored forged-item tooltip plate. */
public record ForgedTooltipComponent(String metalSequence) implements TooltipComponent {
}
