package net.krodark.asterion.port.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.mixin.ItemPropertiesInvoker;
import net.krodark.asterion.network.CrucibleScreenPayload;
import net.krodark.asterion.network.GatewayPortalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.LodestoneTracker;

/** Loader-neutral client features backed by APIs available on both 1.21.1 jars. */
public final class PortClientFeatures {
    private static final ResourceLocation ATMOSPHERE = Asterion.id("dimension/asterion_atmosphere");
    private static boolean initialized;
    private static boolean atmosphereActive;

    private PortClientFeatures() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        // Compass predicates are registered per-item in 1.21.1. Extending CompassItem
        // alone does not make a custom item use the vanilla angle property.
        ItemPropertiesInvoker.asterion$register(Asterion.ANTIKYTHERA_MECHANISM,
                ResourceLocation.withDefaultNamespace("angle"),
                new CompassItemPropertyFunction((level, stack, entity) -> {
                    LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                    return tracker == null ? null : tracker.target().orElse(null);
                }));

        ClientPlayNetworking.registerGlobalReceiver(CrucibleScreenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof PortCrucibleScreen screen
                            && screen.matches(payload.pos())) screen.update(payload);
                    else context.client().setScreen(new PortCrucibleScreen(payload));
                }));
        ClientPlayNetworking.registerGlobalReceiver(GatewayPortalPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PortPortalRenderer.receive(payload)));
        PortPortalRenderer.initialize();
        PortCinematics.initialize();
        PortRagdolls.initialize();
    }

    public static void tick(Minecraft client) {
        PortPortalRenderer.tick(client);
        PortCinematics.tick(client);
        PortRagdolls.tick(client);
        boolean wanted = client.level != null && client.level.dimension().equals(Asterion.ASTERION_LEVEL);
        if (wanted == atmosphereActive) return;
        try {
            var post = VeilRenderSystem.renderer().getPostProcessingManager();
            if (wanted) {
                if (post.getPipeline(ATMOSPHERE) == null) return;
                post.add(20, ATMOSPHERE);
            }
            else post.remove(ATMOSPHERE);
            atmosphereActive = wanted;
        } catch (RuntimeException ignored) {
            // Veil can be between renderer creation and its first resource reload.
        }
    }
}
