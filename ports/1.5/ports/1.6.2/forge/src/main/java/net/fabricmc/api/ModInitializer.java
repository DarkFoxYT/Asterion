package net.fabricmc.api;

/** Source compatibility for the shared initializer invoked by the Forge entrypoint. */
public interface ModInitializer {
    void onInitialize();
}
