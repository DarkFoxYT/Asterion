package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/** Forge entity-load adapter for shared spawn protection. */
public final class ServerEntityEvents {
    public static final EntityLoad ENTITY_LOAD = new EntityLoad();
    private ServerEntityEvents() {}

    @FunctionalInterface public interface Callback { void onLoad(Entity entity, ServerLevel level); }

    public static final class EntityLoad {
        public void register(Callback callback) {
            EntityJoinLevelEvent.BUS.addListener((event, cancelled) -> {
                if (!cancelled && event.getLevel() instanceof ServerLevel level)
                    callback.onLoad(event.getEntity(), level);
            });
        }
    }
}
