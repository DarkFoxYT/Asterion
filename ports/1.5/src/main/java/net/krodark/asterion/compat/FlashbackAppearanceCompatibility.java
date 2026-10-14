package net.krodark.asterion.compat;

import java.lang.reflect.Method;

/** Optional client bridge. Appearance belongs to the current replay connection, never a global skin cache. */
public final class FlashbackAppearanceCompatibility {
    private FlashbackAppearanceCompatibility() {}

    public static boolean isPlayingReplay() {
        Method query = FlashbackApi.IS_IN_REPLAY;
        if (query == null) return false;
        try {
            return Boolean.TRUE.equals(query.invoke(null));
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }

    /** Bind Flashback's synthetic/fallback profile to Essential's recorded outfit stream. */
    public static void bindRecordedAppearance(Object playerInfo, Object connection) {
        EssentialApi.bind(playerInfo, connection);
    }

    private static Method method(String owner, String name, Class<?>... arguments) {
        try {
            return Class.forName(owner, false, FlashbackAppearanceCompatibility.class.getClassLoader())
                    .getMethod(name, arguments);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    private static final class FlashbackApi {
        private static final Method IS_IN_REPLAY = method("com.moulberry.flashback.Flashback", "isInReplay");
    }

    private static final class EssentialApi {
        private static final Method CONNECTION_MANAGER = method(
                "gg.essential.mixins.ext.client.network.NetHandlerPlayClientExt",
                "getEssential$ingameEquippedOutfitsManager");
        private static final Method PROFILE_MANAGER = method(
                "gg.essential.mixins.impl.client.network.NetworkPlayerInfoExt",
                "getEssential$equippedOutfitsManager");
        private static final Method SET_PROFILE_MANAGER = setter();

        private static Method setter() {
            if (PROFILE_MANAGER == null) return null;
            return method("gg.essential.mixins.impl.client.network.NetworkPlayerInfoExt",
                    "setEssential$equippedOutfitsManager", PROFILE_MANAGER.getReturnType());
        }

        private static void bind(Object playerInfo, Object connection) {
            if (CONNECTION_MANAGER == null || PROFILE_MANAGER == null || SET_PROFILE_MANAGER == null
                    || !CONNECTION_MANAGER.getDeclaringClass().isInstance(connection)
                    || !PROFILE_MANAGER.getDeclaringClass().isInstance(playerInfo)) return;
            try {
                Object recorded = CONNECTION_MANAGER.invoke(connection);
                if (recorded != null && PROFILE_MANAGER.invoke(playerInfo) != recorded) {
                    SET_PROFILE_MANAGER.invoke(playerInfo, recorded);
                }
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                // An unsupported optional API must not prevent playback or normal gameplay.
            }
        }
    }
}
