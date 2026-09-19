package net.krodark.asterion.port.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.entity.Entity;

/** Prevents non-model helper entities from falling through an unregistered renderer lookup. */
public final class NoopEntityRenderer<T extends Entity> extends EntityRenderer<T> {
    public NoopEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
