package net.krodark.asterion.game;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;

/** No egg, biome spawn entry, loot, sound or public display name. */
public final class RenderRemnantContent {
    // Reuse the non-networked marker type so existing worlds/replay entity IDs stay unchanged.
    public static final EntityType<Marker> TYPE = EntityType.MARKER;
    private RenderRemnantContent() {}
}
