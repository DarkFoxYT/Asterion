package net.krodark.asterion.test;

import java.lang.reflect.Proxy;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.port.client.PortTextureFrameCache;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import software.bernie.geckolib.cache.texture.AnimatableTexture;

/** Uses the same animated pixels under two namespaces in a real GeckoLib upload. */
final class ForeignTextureRegression {
    static void run(Minecraft client) throws Exception {
        var resources = client.getResourceManager();
        var ownId = Asterion.id("textures/block/torch_greek_fire.png");
        var foreignId = ResourceLocation.tryParse("asterion_tests:" + ownId.getPath());
        var alias = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (args != null && args.length > 0 && args[0] instanceof ResourceLocation id
                            && id.getNamespace().equals("asterion_tests")) {
                        args = args.clone();
                        args[0] = Asterion.id(id.getPath());
                    }
                    try { return method.invoke(resources, args); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                });
        PortTextureFrameCache.clearAll();
        try (var foreign = new AnimatableTexture(foreignId); var own = new AnimatableTexture(ownId)) {
            foreign.load(alias);
            own.load(resources);
            if (!foreign.isAnimated() || !own.isAnimated()) throw new AssertionError("Animation fixture not loaded");
            foreign.setAnimationFrame(1);
            foreign.setAnimationFrame(2);
            foreign.setAnimationFrame(3);
            if (PortTextureFrameCache.usedBytes() != 0)
                throw new AssertionError("Asterion cached another mod's GeckoLib texture");
            own.setAnimationFrame(1);
            if (PortTextureFrameCache.usedBytes() == 0)
                throw new AssertionError("Asterion's own animation cache was disabled");
            long ownBytes = PortTextureFrameCache.usedBytes();
            foreign.setAnimationFrame(4);
            foreign.setAnimationFrame(5);
            if (PortTextureFrameCache.usedBytes() != ownBytes || PortTextureFrameCache.isAsterionUpload())
                throw new AssertionError("Texture ownership leaked between uploads");
            PortTextureFrameCache.withTexture(true, () -> {
                try { PortTextureFrameCache.withTexture(false, () -> { throw new IllegalStateException("fixture"); }); }
                catch (IllegalStateException expected) { }
                if (!PortTextureFrameCache.isAsterionUpload()) throw new AssertionError("Nested upload lost owner");
            });
            if (PortTextureFrameCache.isAsterionUpload()) throw new AssertionError("Upload scope leaked");
        } finally { PortTextureFrameCache.clearAll(); }
        var shader = ResourceLocation.tryParse("minecraft:shaders/core/rendertype_entity_translucent_emissive.json");
        try (var stream = resources.getResource(shader).orElseThrow().open()) {
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream));
            boolean sampler = false;
            for (var entry : json.getAsJsonObject().getAsJsonArray("samplers"))
                sampler |= entry.getAsJsonObject().get("name").getAsString().equals("Sampler2");
            if (!sampler) throw new AssertionError("Shared emissive shader lost its lightmap sampler");
        }
        Asterion.LOGGER.info("ASTERION_COMPAT PASS: foreign GeckoLib bypass, own cache, nested ownership and vanilla emissive sampler");
    }
}
