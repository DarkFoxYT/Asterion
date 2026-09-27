package net.fabricmc.fabric.api.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;

import java.lang.reflect.Method;

/** Forge fire table adapter for declarations shared with Fabric. */
public final class FlammableBlockRegistry {
    private static final FlammableBlockRegistry INSTANCE = new FlammableBlockRegistry();
    private static final Method SET_FLAMMABLE;

    static {
        try {
            SET_FLAMMABLE = FireBlock.class.getDeclaredMethod("setFlammable", Block.class, int.class, int.class);
            SET_FLAMMABLE.setAccessible(true);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private FlammableBlockRegistry() {}

    public static FlammableBlockRegistry getDefaultInstance() {
        return INSTANCE;
    }

    public void add(Block block, int encouragement, int flammability) {
        try {
            SET_FLAMMABLE.invoke((FireBlock) Blocks.FIRE, block, encouragement, flammability);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not register flammability for " + block, error);
        }
    }
}
