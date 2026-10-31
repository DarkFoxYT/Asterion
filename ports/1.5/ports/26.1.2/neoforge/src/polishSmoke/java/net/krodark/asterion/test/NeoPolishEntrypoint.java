package net.krodark.asterion.test;
@net.neoforged.fml.common.Mod(value="asterion_polish_tests",dist=net.neoforged.api.distmarker.Dist.CLIENT)
public final class NeoPolishEntrypoint {
    private boolean checked;
    public NeoPolishEntrypoint() {
        if (Boolean.getBoolean("asterion.earlyPluginSmoke")) {
            try {
                Class.forName("net.krodark.asterion.compat.jade.AsterionJadePlugin");
            } catch (ClassNotFoundException error) {
                throw new AssertionError("Jade integration must be present", error);
            }
        }
        if (Boolean.getBoolean("asterion.updateSmoke")) {
            UpdateMenuRegression.register();
            return;
        }
        new PolishClientSmoke().onInitializeClient();
        if (Boolean.getBoolean("asterion.recipeJoinSmoke")) {
            int[] joinedTicks = {0};
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.player != null && client.level != null && ++joinedTicks[0] >= 40) {
                    net.krodark.asterion.Asterion.LOGGER.info("ASTERION_RECIPE_JOIN PASS: joined world and remained connected after recipe synchronization");
                    client.stop();
                }
            });
        }
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (checked || client.level == null) return;
            int fluids=0,states=0;
            for (var fluid : net.minecraft.core.registries.BuiltInRegistries.FLUID) {
                if (!net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid).getNamespace().equals("asterion")) continue;
                if (fluid.getFluidType()!=net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value())
                    throw new AssertionError("Custom water lost its water fluid type");
                for (var state : fluid.getStateDefinition().getPossibleStates()) {
                    if (state.getFluidType()!=fluid.getFluidType()) throw new AssertionError("Fluid state type mismatch");
                    var model = client.getModelManager().getFluidStateModelSet().get(state);
                    if (model.fluidTintSource() == null || model.fluidTintSource().color(state) != net.krodark.asterion.fluid.HeavyWater.COLOR)
                        throw new AssertionError("Heavy-water tint changed during API migration");
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
