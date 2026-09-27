package net.krodark.asterion.client.render.entity;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import org.joml.Quaternionf;

public final class CentipedeRiderRenderData {
    public static final RenderStateDataKey<Quaternionf> FRAME = RenderStateDataKey.create();
    private CentipedeRiderRenderData() {}
}
