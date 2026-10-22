package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public final class PlayerLookup {
    private PlayerLookup() {}

    public static Collection<ServerPlayer> tracking(Entity entity) {
        return net.krodark.asterion.port.neoforge.NativeLifecycle.tracking(entity);
    }
}
