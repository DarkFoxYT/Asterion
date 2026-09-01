package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class OmegaTreasure {
    private OmegaTreasure() { }
    /** A 23-block Omega inlay keeps the center readable without obstructing boss movement. */
    public static boolean inlay(int x, int z) {
        int r2 = x * x + z * z;
        return z <= 5 && r2 >= 56 && r2 <= 90
                || z >= 5 && z <= 8 && Math.abs(x) >= 4 && Math.abs(x) <= 6
                || z >= 8 && z <= 10 && Math.abs(x) >= 4 && Math.abs(x) <= 11;
    }
    public static void reward(ServerLevel level) {
        // The supplied structure can replace this fallback without changing combat code.
        var authored = level.getStructureManager().get(Asterion.id("omega_treasure"));
        if (authored.isPresent()) {
            var structure = authored.get(); var size = structure.getSize();
            BlockPos origin = new BlockPos(-size.getX() / 2, 37, -size.getZ() / 2);
            if (size.getX() <= 29 && size.getZ() <= 29 && size.getY() <= 20)
                structure.placeInWorld(level, origin, origin, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                        level.getRandom(), 2);
        }
        for (int i = 0; i < 18; i++) {
            double angle = i * Math.PI * 2 / 18;
            ItemStack stack = i < 4 ? new ItemStack(Items.DIAMOND, 2) : i < 8 ? new ItemStack(Items.EMERALD, 4)
                    : new ItemStack(Items.GOLD_INGOT, 6);
            var item = new ItemEntity(level, Math.cos(angle) * 2, 37.5, Math.sin(angle) * 2, stack);
            item.setDeltaMovement(Math.cos(angle) * .1, .2, Math.sin(angle) * .1);
            item.setDefaultPickUpDelay(); level.addFreshEntity(item);
        }
    }
}
