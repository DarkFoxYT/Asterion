package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.util.concurrent.CopyOnWriteArrayList;

public final class ServerEntityEvents {
    private static final CopyOnWriteArrayList<Load> LOAD = new CopyOnWriteArrayList<>();
    public static final Event<Load> ENTITY_LOAD = LOAD::add;

    static {
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level)
                LOAD.forEach(listener -> listener.onLoad(event.getEntity(), level));
        });
    }

    private ServerEntityEvents() {}
    @FunctionalInterface public interface Load { void onLoad(Entity entity, ServerLevel level); }
}
