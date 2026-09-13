package net.krodark.asterion.port.client;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.CursedBrazierEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/** Restores the Cursed Brazier's staged green emissive rings. */
public final class PortCursedBrazierRenderer extends SimpleGeoEntityRenderer<CursedBrazierEntity> {
    public PortCursedBrazierRenderer(EntityRendererProvider.Context context) {
        super(context, Asterion.id("entity/cursed_brazier"),
                Asterion.id("textures/entity/cursed_brazier.png"),
                Asterion.id("entity/cursed_brazier"), 2.35F, 1.0F);
        withEmissiveBones(PortCursedBrazierRenderer::glowColor,
                brazier -> glowAlpha(brazier) > .001F,
                "topglow", "middleglow", "bottomglow", "centerglow");
    }

    private static int glowColor(CursedBrazierEntity brazier) {
        int red = 70 + Math.round((Mth.sin(brazier.tickCount * .16F) + 1F) * 12F);
        int alpha = Math.clamp(Math.round(glowAlpha(brazier) * 255F), 0, 255);
        return alpha << 24 | red << 16 | 0xFF << 8 | 0x72;
    }

    private static float glowAlpha(CursedBrazierEntity brazier) {
        if (brazier.phase() == CursedBrazierEntity.Phase.DORMANT) return 0F;
        if (brazier.phase() != CursedBrazierEntity.Phase.AWAKENING) return 1F;
        float value = Math.clamp((brazier.phaseAge(0) - 10F) / 40F, 0F, 1F);
        return value * value * (3F - 2F * value);
    }
}
