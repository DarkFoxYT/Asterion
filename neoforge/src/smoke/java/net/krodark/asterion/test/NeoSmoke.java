package net.krodark.asterion.test;
@net.neoforged.fml.common.Mod("asterion_tests")
public final class NeoSmoke {
 public NeoSmoke(net.neoforged.bus.api.IEventBus bus) {
  bus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) ->
    event.enqueueWork(() -> new ClientSmokeTest().onInitializeClient()));
 }
}
