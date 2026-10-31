package net.krodark.asterion.test;
public final class FabricPolishEntrypoint implements net.fabricmc.api.ClientModInitializer {
    @Override public void onInitializeClient() {
        if (Boolean.getBoolean("asterion.updateSmoke")) UpdateMenuRegression.register();
        else new PolishClientSmoke().onInitializeClient();
    }
}
