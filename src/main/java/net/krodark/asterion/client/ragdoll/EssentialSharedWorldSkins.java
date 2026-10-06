package net.krodark.asterion.client.ragdoll;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.PlayerSkin;

/** Compatibility for SPS profiles whose texture signatures cannot be verified locally. */
public final class EssentialSharedWorldSkins {
    private record Bridge(Method instance, Method connection, Method sps, Method local, Method parse) { }
    private static final Bridge BRIDGE = load();
    private EssentialSharedWorldSkins() { }

    public static boolean isDefaultSkin(PlayerSkin skin) {
        return RagdollPlayerSkins.isDefault(skin.body().texturePath());
    }

    public static PlayerSkin select(PlayerSkin original, Supplier<PlayerSkin> fallback, boolean sharedWorld) {
        if (!sharedWorld || !isDefaultSkin(original)) return original;
        PlayerSkin candidate = fallback.get();
        return isDefaultSkin(candidate) ? original : candidate;
    }

    public static boolean isSharedWorld() {
        if (BRIDGE == null) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) return false;
        try {
            var server = client.getCurrentServer();
            if (server != null && BRIDGE.parse.invoke(null, server.ip) != null) return true;
            if (!client.hasSingleplayerServer()) return false;
            Object essential = BRIDGE.instance.invoke(null);
            if (essential == null) return false;
            Object connection = BRIDGE.connection.invoke(essential);
            return BRIDGE.local.invoke(BRIDGE.sps.invoke(connection)) != null;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    private static Bridge load() {
        try {
            return new Bridge(Class.forName("gg.essential.Essential").getMethod("getInstance"),
                    Class.forName("gg.essential.Essential").getMethod("getConnectionManager"),
                    Class.forName("gg.essential.network.connectionmanager.ConnectionManager").getMethod("getSpsManager"),
                    Class.forName("gg.essential.network.connectionmanager.sps.SPSManager").getMethod("getLocalSession"),
                    Class.forName("gg.essential.sps.SpsAddress").getMethod("parse", String.class));
        } catch (ReflectiveOperationException | LinkageError absent) { return null; }
    }
}
