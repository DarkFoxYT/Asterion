package net.krodark.asterion.dev;

 
public final class EssentialLaunchSmokeTest {
    private EssentialLaunchSmokeTest() { }
    public static void install() {
        boolean startup = Boolean.getBoolean("asterion.startup.smokeTest");
        if (!startup && !Boolean.getBoolean("asterion.essential.smokeTest")) return;
        int[] ticks = {0};
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle());
            if (startup && (client.getOverlay() != null
                    || !(client.screen instanceof net.minecraft.client.gui.screens.TitleScreen))) return;
            if (++ticks[0] == 80 && startup)
                net.krodark.asterion.Asterion.LOGGER.info("Startup stability check: title screen reached; checking another 26 seconds");
            if (ticks[0] != (startup ? 600 : 80)) return;
            var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
            for (String id : startup ? new String[]{"asterion", "amnetic", "geckolib"}
                    : new String[]{"asterion", "essential", "amnetic", "geckolib"})
                if (!loader.isModLoaded(id)) throw new AssertionError("Missing startup mod: " + id);
            if (loader.isDevelopmentEnvironment()) throw new AssertionError("Essential launch is still using the legacy dev remapper");
            if (startup) {
                var vm = java.lang.management.ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
                if (!vm.getVMOption("TieredStopAtLevel").getValue().equals("3"))
                    throw new AssertionError("Client stability JVM setting was not applied");
                net.krodark.asterion.Asterion.LOGGER.info("PASS: C1 startup stability, 600 client ticks, current runtime mods loaded: {}", loader.getAllMods().size());
            } else net.krodark.asterion.Asterion.LOGGER.info("PASS: Essential, Asterion, GeckoLib and Amnetic initialized together in normal runtime mode");
            client.stop();
        });
    }
}
