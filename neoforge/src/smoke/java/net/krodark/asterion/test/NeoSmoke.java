package net.krodark.asterion.test;
@net.neoforged.fml.common.Mod("asterion_tests")
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
   new ClientSmokeTest().onInitializeClient();
  }));
 }
}
