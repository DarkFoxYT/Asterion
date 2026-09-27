package net.fabricmc.fabric.api.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;

/** Forge/vanilla implementation of Fabric's flammability registry facade. */
public final class FlammableBlockRegistry {
    private static final FlammableBlockRegistry INSTANCE = new FlammableBlockRegistry();

    private FlammableBlockRegistry() {}

    public static FlammableBlockRegistry getDefaultInstance() {
        return INSTANCE;
    }

    public void add(Block block, int burnChance, int spreadChance) {
        ((FireBlock) Blocks.FIRE).setFlammable(block, burnChance, spreadChance);
    }
}
