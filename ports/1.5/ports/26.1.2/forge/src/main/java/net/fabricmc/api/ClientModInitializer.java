package net.fabricmc.api;

/** Source compatibility for the shared client initializer invoked by Forge. */
public interface ClientModInitializer {
    void onInitializeClient();
}
