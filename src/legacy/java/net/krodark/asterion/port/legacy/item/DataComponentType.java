package net.krodark.asterion.port.legacy.item;
import net.minecraft.world.item.ItemStack;
import java.util.function.*;
public record DataComponentType<T>(Function<ItemStack,T> read,BiConsumer<ItemStack,T> write) {}
