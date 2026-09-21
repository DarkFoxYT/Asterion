package net.krodark.asterion.client.ragdoll;

import java.lang.reflect.Method;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Optional ETF bridge: no required dependency or class linkage when ETF is absent. */
final class RagdollTextureCompatibility {
    record Textures(Identifier base, Identifier emissive) { }
    private static final Method VARIANT = method("traben.entity_texture_features.ETFApi",
            "getCurrentETFVariantTextureOfEntity", Entity.class, Identifier.class);
    private static final Method EMISSIVE = method("traben.entity_texture_features.ETFApi",
            "getCurrentETFEmissiveTextureOfEntityOrNull", Entity.class, Identifier.class);
    private static final Method MANAGER = method("traben.entity_texture_features.features.ETFManager", "getInstance");
    private static final Method PLAYER_TEXTURE = method("traben.entity_texture_features.features.ETFManager",
            "getPlayerTexture", Player.class, Identifier.class);
    private static final Method PLAYER_BASE = method("traben.entity_texture_features.features.player.ETFPlayerTexture",
            "getBaseTextureIdentifierOrNullForVanilla", Player.class);
    private static final Method PLAYER_EMISSIVE = method("traben.entity_texture_features.features.player.ETFPlayerTexture",
            "getBaseTextureEmissiveIdentifierOrNullForNone");

    static Textures resolve(Entity owner, Identifier original, boolean playerSkin) {
        if (owner == null || VARIANT == null) return new Textures(original, null);
        try {
            if (playerSkin && owner instanceof Player && MANAGER != null && PLAYER_TEXTURE != null && PLAYER_BASE != null) {
                Object texture = PLAYER_TEXTURE.invoke(MANAGER.invoke(null), owner, original);
                if (texture != null) {
                    Identifier base = (Identifier)PLAYER_BASE.invoke(texture, owner);
                    if (base != null) return new Textures(base, PLAYER_EMISSIVE == null ? null
                            : (Identifier)PLAYER_EMISSIVE.invoke(texture));
                }
            }
            Identifier base = (Identifier)VARIANT.invoke(null, owner, original);
            return new Textures(base == null ? original : base,
                    EMISSIVE == null ? null : (Identifier)EMISSIVE.invoke(null, owner, original));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return new Textures(original, null);
        }
    }

    private static Method method(String type, String name, Class<?>... arguments) {
        try { return Class.forName(type).getMethod(name, arguments); }
        catch (ReflectiveOperationException | LinkageError absent) { return null; }
    }
}
