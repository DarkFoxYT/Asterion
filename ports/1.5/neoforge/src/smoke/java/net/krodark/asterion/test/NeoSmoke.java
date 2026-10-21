package net.krodark.asterion.test;
@net.neoforged.fml.common.Mod(value="asterion_tests", dist=net.neoforged.api.distmarker.Dist.CLIENT)
public final class NeoSmoke {
 public NeoSmoke(net.neoforged.bus.api.IEventBus bus) {
  bus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(() -> {
   if(Boolean.getBoolean("asterion.nativeAmneticSmoke")) {
    if(net.neoforged.fml.ModList.get().isLoaded("connector")) throw new AssertionError("Connector must not be needed by native Amnetic");
    try {
     Class.forName("com.meekdev.amnetic.neoforge.AmneticNeoForge");
     int major=(Integer)Class.forName("org.lwjgl.assimp.Assimp").getMethod("aiGetVersionMajor").invoke(null);
     if(major<5) throw new AssertionError("Invalid Assimp native version: "+major);
     net.krodark.asterion.Asterion.LOGGER.info("ASTERION_NATIVE Amnetic loaded without Connector; embedded Assimp native version {}",major);
    } catch(ReflectiveOperationException error) { throw new AssertionError("Embedded Amnetic native library failed",error); }
   }
   if(Boolean.getBoolean("asterion.startupSmoke")) {
    if(Boolean.getBoolean("asterion.sodiumSmoke") && !net.neoforged.fml.ModList.get().isLoaded("sodium"))
     throw new AssertionError("Sodium must be installed for the compatibility smoke test");
    int[] titleTicks = {0};
    long deadline = System.nanoTime() + 120_000_000_000L;
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
     if(System.nanoTime() > deadline) throw new AssertionError("Packaged client did not reach the title screen within two minutes");
     if(client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
      client.options.onboardingAccessibilityFinished();
      client.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
     }
     if(client.screen instanceof net.minecraft.client.gui.screens.TitleScreen && client.getOverlay() == null && ++titleTicks[0] >= 20) {
      net.krodark.asterion.Asterion.LOGGER.info("ASTERION_STARTUP PASS: packaged client reached title screen; Sodium={}",net.neoforged.fml.ModList.get().isLoaded("sodium"));
      client.stop();
     }
    });
   } else new ClientSmokeTest().onInitializeClient();
  }));
 }
}
