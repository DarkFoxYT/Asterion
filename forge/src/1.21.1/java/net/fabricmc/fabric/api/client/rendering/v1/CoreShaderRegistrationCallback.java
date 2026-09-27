package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@FunctionalInterface
public interface CoreShaderRegistrationCallback {
    List<CoreShaderRegistrationCallback> LISTENERS = new ArrayList<>();
    Event<CoreShaderRegistrationCallback> EVENT = LISTENERS::add;
    void registerShaders(RegistrationContext context) throws IOException;
    static void fire(RegistrationContext context) throws IOException {
        for (var listener : LISTENERS) listener.registerShaders(context);
    }
    @FunctionalInterface interface RegistrationContext {
        void register(ResourceLocation id, VertexFormat format, Consumer<ShaderInstance> loaded) throws IOException;
    }
}
