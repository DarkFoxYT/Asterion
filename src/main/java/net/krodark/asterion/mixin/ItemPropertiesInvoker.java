package net.krodark.asterion.mixin;

import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemProperties.class)
public interface ItemPropertiesInvoker {
    @Invoker("register")
    static void asterion$register(Item item, ResourceLocation id,
                                  ClampedItemPropertyFunction property) {
        throw new AssertionError();
    }
}
