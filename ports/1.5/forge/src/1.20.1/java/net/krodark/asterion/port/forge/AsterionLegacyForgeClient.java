package net.krodark.asterion.port.forge;
/** Forgified Fabric API forwards the version-matched client lifecycle and render events. */
public final class AsterionLegacyForgeClient {
 private AsterionLegacyForgeClient(){}
 public static void initialize(){new net.krodark.asterion.port.fabric.AsterionFabricClient().onInitializeClient();}
}
