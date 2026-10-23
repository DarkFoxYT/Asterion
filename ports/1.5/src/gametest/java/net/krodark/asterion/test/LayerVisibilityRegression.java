package net.krodark.asterion.test;

import com.meekdev.amnetic.client.entityfx.internal.TextureOverrideRegistry;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/** Checks the composed result without leaving a texture override on the player. */
public final class LayerVisibilityRegression {
    public static void run(Minecraft client) {
        var registry = TextureOverrideRegistry.INSTANCE;
        try {
            var hook = java.util.Arrays.stream(LivingEntityRenderer.class.getDeclaredMethods())
                    .filter(method -> method.getName().contains("amnetic$hideLayers")
                            && method.getParameterCount() == 2 && method.getParameterTypes()[0] == boolean.class)
                    .findFirst().orElseThrow(() -> new AssertionError("Composable layer hook is missing; merged hooks: "
                            + java.util.Arrays.stream(LivingEntityRenderer.class.getDeclaredMethods())
                            .filter(method -> method.getName().contains("amnetic"))
                            .map(java.lang.reflect.Method::toString).toList()));
            hook.setAccessible(true);
            var renderer = client.getEntityRenderDispatcher().getRenderer(client.player);
            if ((Boolean)hook.invoke(renderer, false, client.player)
                    || !(Boolean)hook.invoke(renderer, true, client.player))
                throw new AssertionError("Another mod's layer visibility result was overwritten");
            registry.put(client.player, ResourceLocation.tryParse("minecraft:textures/entity/player/wide/steve.png"), true);
            if (!(Boolean)hook.invoke(renderer, false, client.player))
                throw new AssertionError("Amnetic layer hiding no longer works");
            registry.put(client.player, ResourceLocation.tryParse("minecraft:textures/entity/player/wide/steve.png"), false);
            if ((Boolean)hook.invoke(renderer, false, client.player)
                    || !(Boolean)hook.invoke(renderer, true, client.player))
                throw new AssertionError("Texture-only overrides changed another mod's visibility result");
            Asterion.LOGGER.info("ASTERION_LAYER_VISIBILITY PASS: external visibility result and Amnetic layer hiding preserved");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        finally { registry.remove(client.player); }
    }
}
