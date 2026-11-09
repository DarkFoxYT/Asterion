package net.krodark.asterion.test;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.port.compat.ItemData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import java.util.Optional;

final class ComponentRemovalChecks {
    static void run(ServerLevel level) {
        var stack = new ItemStack(Asterion.ANTIKYTHERA_MECHANISM);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("preserved"));
        ItemData.set(stack, DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), BlockPos.ZERO)), true));
        ItemData.remove(stack, DataComponents.LODESTONE_TRACKER);
        ItemData.remove(stack, DataComponents.LODESTONE_TRACKER);
        if (ItemData.get(stack, DataComponents.LODESTONE_TRACKER) != null)
            throw new AssertionError("Compass target remained after removal");
        if (!stack.getHoverName().getString().equals("preserved"))
            throw new AssertionError("Removing a compass target changed unrelated item data");
    }
}
