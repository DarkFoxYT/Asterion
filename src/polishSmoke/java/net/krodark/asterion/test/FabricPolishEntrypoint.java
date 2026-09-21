package net.krodark.asterion.test;
public final class FabricPolishEntrypoint implements net.fabricmc.api.ClientModInitializer {
    @Override public void onInitializeClient() { new PolishClientSmoke().onInitializeClient(); }
}
