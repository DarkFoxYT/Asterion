package net.krodark.asterion.client.ragdoll;

import java.lang.reflect.Method;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Run optional EMF animations for the ragdoll owner, not the last normally rendered player. */
final class RagdollModelCompatibility {
    private static final Bridge BRIDGE = load();
    private record Bridge(Method root, Method animate) { }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean setup(EntityModel model, EntityRenderState state) {
        model.setupAnim(state);
        if (BRIDGE == null) return false;
        try {
            Object root = BRIDGE.root.invoke(model);
            // EMF's API mounts this player's state and restores the previous state in a finally block.
            return root != null && Boolean.TRUE.equals(BRIDGE.animate.invoke(null, state, root, true));
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    private static Bridge load() {
        try {
            Class<?> root = Class.forName("traben.entity_model_features.models.parts.EMFModelPartRoot");
            return new Bridge(Class.forName("traben.entity_model_features.models.IEMFModel").getMethod("emf$getEMFRootModel"),
                    Class.forName("traben.entity_model_features.EMFAnimationApi").getMethod("animateModelForState",
                            EntityRenderState.class, root, boolean.class));
        } catch (ReflectiveOperationException | LinkageError absent) { return null; }
    }
}
