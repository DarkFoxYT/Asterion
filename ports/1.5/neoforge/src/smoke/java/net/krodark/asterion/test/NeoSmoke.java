package net.krodark.asterion.test;
@net.neoforged.fml.common.Mod(value="asterion_tests", dist=net.neoforged.api.distmarker.Dist.CLIENT)
public final class NeoSmoke {
 public NeoSmoke(net.neoforged.bus.api.IEventBus bus) {
  bus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(() -> {
   if(Boolean.getBoolean("asterion.embeddiumSmoke")) {
    if(!net.neoforged.fml.ModList.get().isLoaded("embeddium"))
     throw new AssertionError("Embeddium must be installed for this compatibility test");
    try {
     Class<?> interpolation=Class.forName("net.minecraft.client.renderer.texture.SpriteContents$InterpolationData");
     for(var method:interpolation.getDeclaredMethods()) {
      if(method.getName().contains("asterion$"))
       throw new AssertionError("Asterion interpolation mixin must yield to Embeddium: "+method);
     }
     net.krodark.asterion.Asterion.LOGGER.info("ASTERION_EMBEDDIUM PASS: renderer loaded, Asterion interpolation hooks excluded");
    } catch(ClassNotFoundException error) { throw new AssertionError(error); }
   }
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
    if(!net.neoforged.fml.ModList.get().isLoaded("embeddium") && !net.neoforged.fml.ModList.get().isLoaded("sodium")) {
     try {
      boolean ownCache=java.util.Arrays.stream(Class.forName("net.minecraft.client.renderer.texture.SpriteContents$InterpolationData").getDeclaredMethods())
       .anyMatch(method -> method.getName().contains("asterion$cached"));
      if(!ownCache) throw new AssertionError("Vanilla interpolation cache was disabled without an alternative renderer");
      net.krodark.asterion.Asterion.LOGGER.info("ASTERION_INTERPOLATION PASS: vanilla animation cache remains active without a renderer mod");
     } catch(ClassNotFoundException error) { throw new AssertionError(error); }
    }
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
      if(Boolean.getBoolean("asterion.embeddiumSmoke")) {
       if(titleTicks[0] == 20 || titleTicks[0] == 40) client.getWindow().toggleFullScreen();
       if(titleTicks[0] < 60) return;
       try(var frame=net.minecraft.client.Screenshot.takeScreenshot(client.getMainRenderTarget())) {
        int visible=0;
        for(int y=1;y<10;y++) for(int x=1;x<10;x++)
         if((frame.getPixelRGBA(frame.getWidth()*x/10,frame.getHeight()*y/10)&0xFFFFFF)!=0) visible++;
        if(visible<20) throw new AssertionError("Black framebuffer after fullscreen/windowed transitions");
        net.krodark.asterion.Asterion.LOGGER.info("ASTERION_EMBEDDIUM fullscreen PASS: both transitions completed, title framebuffer contains {} visible samples",visible);
       }
       net.minecraft.client.Screenshot.grab(client.gameDirectory,"embeddium-title-after-fullscreen.png",client.getMainRenderTarget(),message -> {});
      }
      net.krodark.asterion.Asterion.LOGGER.info("ASTERION_STARTUP PASS: packaged client reached title screen; Sodium={}",net.neoforged.fml.ModList.get().isLoaded("sodium"));
      client.stop();
     }
    });
   } else new ClientSmokeTest().onInitializeClient();
  }));
 }
}
