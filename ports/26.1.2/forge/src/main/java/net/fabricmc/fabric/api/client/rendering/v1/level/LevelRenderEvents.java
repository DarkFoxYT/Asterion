package net.fabricmc.fabric.api.client.rendering.v1.level;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Forge-side world render callbacks driven by ForgeLevelRendererMixin. */
public final class LevelRenderEvents {
    public static final RenderEvent AFTER_TRANSLUCENT_TERRAIN = new RenderEvent();
    public static final RenderEvent BEFORE_TRANSLUCENT_TERRAIN = new RenderEvent();
    public static final ExtractionEvent END_EXTRACTION = new ExtractionEvent();
    private LevelRenderEvents() {}

    public static final class RenderEvent {
        private final List<Consumer<LevelRenderContext>> listeners = new CopyOnWriteArrayList<>();
        public void register(Consumer<LevelRenderContext> listener) { listeners.add(listener); }
        public void fire(LevelRenderContext context) { listeners.forEach(listener -> listener.accept(context)); }
    }

    public static final class ExtractionEvent {
        private final List<Consumer<LevelExtractionContext>> listeners = new CopyOnWriteArrayList<>();
        public void register(Consumer<LevelExtractionContext> listener) { listeners.add(listener); }
        public void fire(LevelExtractionContext context) { listeners.forEach(listener -> listener.accept(context)); }
    }
}
