package net.krodark.asterion.test;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.port.compat.ItemData;
import net.krodark.asterion.port.legacy.item.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class ComponentRemovalChecks {
    static void run(ServerLevel level) {
        var stack = new ItemStack(Asterion.ANTIKYTHERA_MECHANISM);
        var tag = stack.getOrCreateTag();
        tag.put("LodestonePos", NbtUtils.writeBlockPos(BlockPos.ZERO));
        tag.putString("LodestoneDimension", level.dimension().location().toString());
        tag.putBoolean("LodestoneTracked", true);
        tag.putString("asterion.regression", "preserved");
        ItemData.remove(stack, DataComponents.LODESTONE_TRACKER);
        ItemData.remove(stack, DataComponents.LODESTONE_TRACKER);
        if (tag.contains("LodestonePos") || tag.contains("LodestoneDimension") || tag.contains("LodestoneTracked"))
            throw new AssertionError("Legacy compass target remained after removal");
        if (!tag.getString("asterion.regression").equals("preserved"))
            throw new AssertionError("Removing a compass target changed unrelated item data");
    }
}
