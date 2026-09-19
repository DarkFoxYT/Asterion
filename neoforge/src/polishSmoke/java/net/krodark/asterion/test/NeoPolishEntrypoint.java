package net.krodark.asterion.test;
@net.neoforged.fml.common.Mod(value="asterion_polish_tests",dist=net.neoforged.api.distmarker.Dist.CLIENT)
public final class NeoPolishEntrypoint {
    public NeoPolishEntrypoint() { new PolishClientSmoke().onInitializeClient(); }
}
