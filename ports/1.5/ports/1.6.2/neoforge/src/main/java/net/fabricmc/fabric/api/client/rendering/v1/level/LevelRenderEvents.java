package net.fabricmc.fabric.api.client.rendering.v1.level;

import net.fabricmc.fabric.api.event.Event;

import java.util.ArrayList;
import java.util.List;

public final class LevelRenderEvents {
    private static final List<BeforeTranslucentTerrain> BEFORE = new ArrayList<>();
    private static final List<AfterTranslucentTerrain> AFTER = new ArrayList<>();
    private static final List<EndMain> END = new ArrayList<>();
    private static final List<EndExtraction> EXTRACTION = new ArrayList<>();
    public static final Event<BeforeTranslucentTerrain> BEFORE_TRANSLUCENT_TERRAIN =
            new Event<>(BEFORE::add);
    public static final Event<AfterTranslucentTerrain> AFTER_TRANSLUCENT_TERRAIN =
            new Event<>(AFTER::add);
    public static final Event<EndMain> END_MAIN = new Event<>(END::add);
    public static final Event<EndExtraction> END_EXTRACTION = new Event<>(EXTRACTION::add);

    private LevelRenderEvents() {}

    public static void before(LevelRenderContext context) {
        BEFORE.forEach(it -> it.beforeTranslucentTerrain(context));
    }

    public static void after(LevelRenderContext context) {
        AFTER.forEach(it -> it.afterTranslucentTerrain(context));
    }

    public static void end(LevelRenderContext context) {
        END.forEach(it -> it.endMain(context));
    }

    public static void extract(LevelExtractionContext context) {
        EXTRACTION.forEach(it -> it.endExtraction(context));
    }

    @FunctionalInterface
    public interface BeforeTranslucentTerrain {
        void beforeTranslucentTerrain(LevelRenderContext context);
    }

    @FunctionalInterface
    public interface AfterTranslucentTerrain {
        void afterTranslucentTerrain(LevelRenderContext context);
    }

    @FunctionalInterface
    public interface EndMain {
        void endMain(LevelRenderContext context);
    }

    @FunctionalInterface
    public interface EndExtraction {
        void endExtraction(LevelExtractionContext context);
    }
}
