package net.krodark.asterion.test;
@net.neoforged.fml.common.Mod(value="asterion_polish_tests",dist=net.neoforged.api.distmarker.Dist.CLIENT)
public final class NeoPolishEntrypoint {
    private boolean checked;
    public NeoPolishEntrypoint() {
        new PolishClientSmoke().onInitializeClient();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (checked || client.level == null) return;
            int fluids=0,states=0;
            for (var fluid : net.minecraft.core.registries.BuiltInRegistries.FLUID) {
                if (!net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid).getNamespace().equals("asterion")) continue;
                if (fluid.getFluidType()!=net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value())
                    throw new AssertionError("Custom water lost its water fluid type");
                for (var state : fluid.getStateDefinition().getPossibleStates()) {
                    if (state.getFluidType()!=fluid.getFluidType()) throw new AssertionError("Fluid state type mismatch");
                    states++;
                }
                fluids++;
            }
            if(fluids<3 || states<3)throw new AssertionError("Missing custom water fixtures");
            checked=true;
            net.krodark.asterion.Asterion.LOGGER.info("ASTERION_FLUID_TYPES PASSED: {} custom fluids, {} states",fluids,states);
        });
    }
}
