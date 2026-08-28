package net.krodark.asterion.client.ragdoll;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

public final class RagdollRenderData {
    public static final RenderStateDataKey<Integer> ENTITY_ID = RenderStateDataKey.create(() -> "asterion:ragdoll_entity_id");
    private RagdollRenderData() { }
}
