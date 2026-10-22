package net.fabricmc.fabric.api.client.rendering.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

public final class WorldRenderEvents {
    private static final List<Start> START_LIST = new ArrayList<>();
    private static final List<AfterSetup> SETUP_LIST = new ArrayList<>();
    private static final List<AfterEntities> ENTITIES_LIST = new ArrayList<>();
    private static final List<BeforeBlockOutline> OUTLINE_LIST = new ArrayList<>();
    private static final List<DebugRender> DEBUG_LIST = new ArrayList<>();
    private static final List<AfterTranslucent> TRANSLUCENT_LIST = new ArrayList<>();
    private static final List<BeforeEntities> BEFORE_ENTITIES_LIST = new ArrayList<>();
    private static final List<End> END_LIST = new ArrayList<>();
    public static final Event<Start> START = new Event<>(START_LIST::add);
    public static final Event<AfterSetup> AFTER_SETUP = new Event<>(SETUP_LIST::add);
    public static final Event<AfterEntities> AFTER_ENTITIES = new Event<>(ENTITIES_LIST::add);
    public static final Event<BeforeBlockOutline> BEFORE_BLOCK_OUTLINE =
            new Event<>(OUTLINE_LIST::add);
    public static final Event<DebugRender> BEFORE_DEBUG_RENDER = new Event<>(DEBUG_LIST::add);
    public static final Event<AfterTranslucent> AFTER_TRANSLUCENT =
            new Event<>(TRANSLUCENT_LIST::add);
    public static final Event<BeforeEntities> BEFORE_ENTITIES =
            new Event<>(BEFORE_ENTITIES_LIST::add);
    public static final Event<End> END = new Event<>(END_LIST::add);

    private WorldRenderEvents() {}

    public static void fireStart(WorldRenderContext c) {
        START_LIST.forEach(it -> it.onStart(c));
    }

    public static void fireAfterSetup(WorldRenderContext c) {
        SETUP_LIST.forEach(it -> it.afterSetup(c));
    }

    public static void fireAfterEntities(WorldRenderContext c) {
        ENTITIES_LIST.forEach(it -> it.afterEntities(c));
    }

    public static void fireBeforeBlockOutline(WorldRenderContext c, HitResult h) {
        OUTLINE_LIST.forEach(it -> it.beforeBlockOutline(c, h));
    }

    public static void fireDebug(WorldRenderContext c) {
        DEBUG_LIST.forEach(it -> it.beforeDebugRender(c));
    }

    public static void fireTranslucent(WorldRenderContext c) {
        TRANSLUCENT_LIST.forEach(it -> it.afterTranslucent(c));
    }

    public static void fireBeforeEntities(WorldRenderContext c) {
        BEFORE_ENTITIES_LIST.forEach(it -> it.beforeEntities(c));
    }

    public static void fireEnd(WorldRenderContext c) {
        END_LIST.forEach(it -> it.onEnd(c));
    }

    @FunctionalInterface
    public interface Start {
        void onStart(WorldRenderContext c);
    }

    @FunctionalInterface
    public interface AfterSetup {
        void afterSetup(WorldRenderContext c);
    }

    @FunctionalInterface
    public interface AfterEntities {
        void afterEntities(WorldRenderContext c);
    }

    @FunctionalInterface
    public interface BeforeBlockOutline {
        boolean beforeBlockOutline(WorldRenderContext c, HitResult h);
    }

    @FunctionalInterface
    public interface DebugRender {
        void beforeDebugRender(WorldRenderContext c);
    }

    @FunctionalInterface
    public interface AfterTranslucent {
        void afterTranslucent(WorldRenderContext c);
    }

    @FunctionalInterface
    public interface BeforeEntities {
        void beforeEntities(WorldRenderContext c);
    }

    @FunctionalInterface
    public interface End {
        void onEnd(WorldRenderContext c);
    }
}
