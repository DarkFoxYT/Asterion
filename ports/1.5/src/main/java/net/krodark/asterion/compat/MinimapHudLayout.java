package net.krodark.asterion.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reads optional public Xaero HUD bounds without retaining sessions or worlds. */
public final class MinimapHudLayout {
    private static boolean initialized;
    private static Field instance, x, y, w, h;
    private static Method getHud, getManager, getModules, getSession, getState, isActive;
    private static Class<?> minimapSession;

    private MinimapHudLayout() {}

    public static int top(int panelWidth, int panelHeight, int screenHeight) {
        try {
            if (!initialized) {
                initialized = true;
                ClassLoader loader = MinimapHudLayout.class.getClassLoader();
                Class<?> mod = Class.forName("xaero.common.HudMod", false, loader);
                instance = mod.getField("INSTANCE");
                getHud = mod.getMethod("getHud");
                getManager = getHud.getReturnType().getMethod("getModuleManager");
                getModules = getManager.getReturnType().getMethod("getModules");
                Class<?> module = Class.forName("xaero.hud.module.HudModule", false, loader);
                getSession = module.getMethod("getCurrentSession");
                getState = module.getMethod("getPushState");
                isActive = getSession.getReturnType().getMethod("isActive");
                minimapSession = Class.forName("xaero.hud.minimap.module.MinimapSession", false, loader);
                Class<?> state = getState.getReturnType();
                x = state.getField("x"); y = state.getField("y");
                w = state.getField("w"); h = state.getField("h");
            }
            if (h == null) return 12;
            Object hud = getHud.invoke(instance.get(null));
            for (Object module : (Iterable<?>) getModules.invoke(getManager.invoke(hud))) {
                Object session = getSession.invoke(module);
                if (!minimapSession.isInstance(session) || !Boolean.TRUE.equals(isActive.invoke(session))) continue;
                Object bounds = getState.invoke(module);
                int left=x.getInt(bounds), top=y.getInt(bounds), width=w.getInt(bounds), height=h.getInt(bounds);
                if (width > 0 && height > 0 && left < 12 + panelWidth && left + width > 12
                        && top < 12 + panelHeight && top + height > 12) {
                    int below = top + height + 8;
                    if (below + panelHeight <= screenHeight - 30) return below;
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            // Missing or older APIs use the normal layout.
        }
        return 12;
    }
}
