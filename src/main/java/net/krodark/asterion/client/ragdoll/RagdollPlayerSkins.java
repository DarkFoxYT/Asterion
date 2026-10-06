package net.krodark.asterion.client.ragdoll;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/** Keep a loaded skin through temporary profile/skin reloads in a multiplayer session. */
final class RagdollPlayerSkins {
    private static final Map<UUID, PlayerSkin> LOADED = new LinkedHashMap<>();

    static PlayerSkin resolve(AbstractClientPlayer player) {
        return remember(player.getUUID(), player.getSkin());
    }

    static PlayerSkin remember(UUID id, PlayerSkin current) {
        PlayerSkin previous = LOADED.get(id);
        if (isDefault(current.body().texturePath()) && previous != null
                && !isDefault(previous.body().texturePath())) {
            // Preserve the body/model, but let live cape and equipment changes through.
            return new PlayerSkin(previous.body(), current.cape(), current.elytra(),
                    previous.model(), previous.secure());
        }
        LOADED.put(id, current);
        if (LOADED.size() > 128) LOADED.remove(LOADED.keySet().iterator().next());
        return current;
    }

    static boolean isDefault(Identifier texture) {
        return texture.getNamespace().equals("minecraft")
                && texture.getPath().startsWith("textures/entity/player/");
    }

    static void clear() { LOADED.clear(); }
}
