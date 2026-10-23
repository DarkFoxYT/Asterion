from pathlib import Path
import sys

base=Path(__file__).resolve().parents[1]
target=Path(sys.argv[1])/'net/krodark/asterion/test'
target.mkdir(parents=True,exist_ok=True)
source=(base/'neoforge/src/smoke/java/net/krodark/asterion/test/NeoServerSmoke.java').read_text('utf8')
source=source.replace('net.neoforged.neoforge.common.NeoForge','net.minecraftforge.common.MinecraftForge').replace('NeoForge.EVENT_BUS','MinecraftForge.EVENT_BUS')
source=source.replace('net.neoforged.neoforge.event.tick.ServerTickEvent.Post','net.minecraftforge.event.TickEvent.ServerTickEvent')
source=source.replace('net.neoforged.neoforge.','net.minecraftforge.').replace('net.neoforged.api.','net.minecraftforge.api.').replace('net.neoforged.fml.','net.minecraftforge.fml.')
source=source.replace('@Mod(value = "asterion_tests", dist = Dist.DEDICATED_SERVER)','@Mod("asterion_tests")')
source=source.replace('public NeoServerSmoke() {','public NeoServerSmoke() {\n        if(net.minecraftforge.fml.loading.FMLEnvironment.dist==net.minecraftforge.api.distmarker.Dist.CLIENT)NativeLegacyTestClient.register();\n        if(!Boolean.getBoolean("asterion.serverSmoke"))return;')
source=source.replace('if (pendingChecks != null) {','if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;\n            if (pendingChecks != null) {')
source=source.replace('player.connection.getConnection()','player.connection.connection')
source=source.replace(',\n                net.minecraft.server.level.ClientInformation.createDefault()', '')
source=source.replace('var level = server.overworld();','var level = server.overworld();\n        NativeLegacyRecipeChecks.run(level);\n        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(),true,level.registryAccess());')
(target/'NeoServerSmoke.java').write_text(source,'utf8')
gameplay=(base/'src/gametest/1.20.1/net/krodark/asterion/test/VersionGameplay.java').read_text('utf8')
helpers=gameplay.split(' static net.minecraft.world.inventory.CraftingContainer',1)[1].split(' static ItemStack craft(',1)[0]
helpers=' static net.minecraft.world.inventory.CraftingContainer'+helpers
helpers=helpers.replace('static void vanillaTools(', 'static void run(').replace('GameplaySmoke.require','require')
(target/'NativeLegacyRecipeChecks.java').write_text('package net.krodark.asterion.test;\nimport net.minecraft.world.item.ItemStack;\nfinal class NativeLegacyRecipeChecks {\n'+helpers+'\nprivate static void require(boolean value,String message){if(!value)throw new AssertionError(message);}\n}\n','utf8')

(target/'NativeLegacyTestClient.java').write_text("""package net.krodark.asterion.test;
public final class NativeLegacyTestClient {
 public static void register(){
  net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event)->event.enqueueWork(()->{
   net.krodark.asterion.Asterion.LOGGER.info("LEGACY_CLIENT_TEST registered");
   new ClientSmokeTest().onInitializeClient();
   int[] ticks={0};
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ClientTickEvent tick)->{
    if(tick.phase==net.minecraftforge.event.TickEvent.Phase.END && ++ticks[0]%100==0){
     var client=net.minecraft.client.Minecraft.getInstance();
     net.krodark.asterion.Asterion.LOGGER.info("LEGACY_CLIENT_TEST screen={} overlay={}",client.screen,client.getOverlay());
    }
   });
  }));
 }
}
""",'utf8')
for path in (base/'neoforge/src/main/java/net/fabricmc/loader').rglob('*.java'):
 relative=path.relative_to(base/'neoforge/src/main/java')
 output=Path(sys.argv[1])/relative
 output.parent.mkdir(parents=True,exist_ok=True)
 output.write_text(path.read_text('utf8').replace('net.neoforged.fml.','net.minecraftforge.fml.'),'utf8')
