package net.krodark.asterion.port.client.ragdoll;

import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Optional ETF bridge: no required dependency or class linkage when ETF is absent. */
final class RagdollTextureCompatibility {
    record Textures(ResourceLocation base, ResourceLocation emissive) { }
    private static final Method VARIANT = method("traben.entity_texture_features.ETFApi",
            "getCurrentETFVariantTextureOfEntity", Entity.class, ResourceLocation.class);
    private static final Method EMISSIVE = method("traben.entity_texture_features.ETFApi",
            "getCurrentETFEmissiveTextureOfEntityOrNull", Entity.class, ResourceLocation.class);
    private static final Method MANAGER = method("traben.entity_texture_features.features.ETFManager", "getInstance");
    private static final Method PLAYER_TEXTURE = method("traben.entity_texture_features.features.ETFManager",
            "getPlayerTexture", Player.class, ResourceLocation.class);
    private static final Method PLAYER_BASE = method("traben.entity_texture_features.features.player.ETFPlayerTexture",
            "getBaseTextureResourceLocationOrNullForVanilla", Player.class);
    private static final Method PLAYER_EMISSIVE = method("traben.entity_texture_features.features.player.ETFPlayerTexture",
            "getBaseTextureEmissiveResourceLocationOrNullForNone");

    static Textures resolve(Entity owner, ResourceLocation original, boolean playerSkin) {
        if (owner == null || VARIANT == null) return new Textures(original, null);
        try {
            if (playerSkin && owner instanceof Player && MANAGER != null && PLAYER_TEXTURE != null && PLAYER_BASE != null) {
                Object texture = PLAYER_TEXTURE.invoke(MANAGER.invoke(null), owner, original);
                if (texture != null) {
                    ResourceLocation base = (ResourceLocation)PLAYER_BASE.invoke(texture, owner);
                    if (base != null) return new Textures(base, PLAYER_EMISSIVE == null ? null
                            : (ResourceLocation)PLAYER_EMISSIVE.invoke(texture));
                }
            }
            ResourceLocation base = (ResourceLocation)VARIANT.invoke(null, owner, original);
            return new Textures(base == null ? original : base,
                    EMISSIVE == null ? null : (ResourceLocation)EMISSIVE.invoke(null, owner, original));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return new Textures(original, null);
        }
    }

    private static Method method(String type, String name, Class<?>... arguments) {
        try { return Class.forName(type).getMethod(name, arguments); }
        catch (ReflectiveOperationException | LinkageError absent) { return null; }
    }
}
