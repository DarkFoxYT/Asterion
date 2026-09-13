package net.krodark.asterion.port.client;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.ConstructEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Construct renderer with both authored energy shells in the Veil emission pass. */
public final class PortConstructRenderer extends SimpleGeoEntityRenderer<ConstructEntity> {
    public PortConstructRenderer(EntityRendererProvider.Context context) {
        super(context, Asterion.id("entity/construct"), Asterion.id("textures/entity/construct.png"),
                Asterion.id("entity/construct"), 0.55F, 1.0F);
        withEmissiveBones("glowbody", "glowhead");
    }
}
