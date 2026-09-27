package com.meekdev.amnetic.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** Registers shared client hooks before NeoForge freezes key bindings. */
@Mod(value = "amnetic", dist = Dist.CLIENT)
public final class AmneticNeoForge {
    public AmneticNeoForge() {
        new com.meekdev.amnetic.client.AmneticClient().onInitializeClient();
        org.slf4j.LoggerFactory.getLogger("Amnetic").info("Amnetic native NeoForge renderer initialized; Connector is not required");
    }
}
