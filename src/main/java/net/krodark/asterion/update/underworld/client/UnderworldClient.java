package net.krodark.asterion.update.underworld.client;

import net.krodark.asterion.update.underworld.UnderworldContent;
import net.minecraft.client.renderer.entity.EntityRenderers;

public final class UnderworldClient {
    private UnderworldClient() { }
    public static void initialize() {
        EntityRenderers.register(UnderworldContent.CHARONS_FERRY, CharonsFerryRenderer::new);
        UnderworldPostEffects.register();
    }
}
