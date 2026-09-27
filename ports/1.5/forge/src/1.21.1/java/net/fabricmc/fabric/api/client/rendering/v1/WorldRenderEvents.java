package net.fabricmc.fabric.api.client.rendering.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.phys.HitResult;
import java.util.ArrayList;
import java.util.List;

/** Forge-backed event lists for the shared Asterion world render passes. */
public final class WorldRenderEvents {
    private static final List<Start> START_LIST = new ArrayList<>();
    private static final List<AfterSetup> SETUP_LIST = new ArrayList<>();
    private static final List<AfterEntities> ENTITIES_LIST = new ArrayList<>();
    private static final List<BeforeBlockOutline> OUTLINE_LIST = new ArrayList<>();
    public static final Event<Start> START = START_LIST::add;
    public static final Event<AfterSetup> AFTER_SETUP = SETUP_LIST::add;
    public static final Event<AfterEntities> AFTER_ENTITIES = ENTITIES_LIST::add;
    public static final Event<BeforeBlockOutline> BEFORE_BLOCK_OUTLINE = OUTLINE_LIST::add;

    private WorldRenderEvents() {}
    public static void fireStart(WorldRenderContext context) { START_LIST.forEach(it -> it.onStart(context)); }
    public static void fireAfterSetup(WorldRenderContext context) { SETUP_LIST.forEach(it -> it.afterSetup(context)); }
    public static void fireAfterEntities(WorldRenderContext context) { ENTITIES_LIST.forEach(it -> it.afterEntities(context)); }
    public static void fireBeforeBlockOutline(WorldRenderContext context, HitResult hit) {
        OUTLINE_LIST.forEach(it -> it.beforeBlockOutline(context, hit));
    }

    @FunctionalInterface public interface Start { void onStart(WorldRenderContext context); }
    @FunctionalInterface public interface AfterSetup { void afterSetup(WorldRenderContext context); }
    @FunctionalInterface public interface AfterEntities { void afterEntities(WorldRenderContext context); }
    @FunctionalInterface public interface BeforeBlockOutline {
        boolean beforeBlockOutline(WorldRenderContext context, HitResult hit);
    }
}
