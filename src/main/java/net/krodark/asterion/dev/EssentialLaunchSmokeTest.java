package net.krodark.asterion.dev;

/** Explicit opt-in startup check; never runs during ordinary play. */
public final class EssentialLaunchSmokeTest {
    private EssentialLaunchSmokeTest() { }
    public static void install() {
        if (!Boolean.getBoolean("asterion.essential.smokeTest")) return;
        int[] ticks = {0};
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle());
            if (++ticks[0] != 80) return;
            var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
            for (String id : new String[]{"asterion", "essential", "amnetic", "geckolib"})
                if (!loader.isModLoaded(id)) throw new AssertionError("Missing startup mod: " + id);
            if (loader.isDevelopmentEnvironment()) throw new AssertionError("Essential launch is still using the legacy dev remapper");
            net.krodark.asterion.Asterion.LOGGER.info("PASS: Essential, Asterion, GeckoLib and Amnetic initialized together in normal runtime mode");
            client.stop();
        });
    }
}
