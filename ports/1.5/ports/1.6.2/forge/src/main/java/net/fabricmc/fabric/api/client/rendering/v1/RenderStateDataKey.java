package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.function.Supplier;

/** Identity key for Forge-side entity render state attachments. */
public final class RenderStateDataKey<T> {
    private final Supplier<String> name;
    private RenderStateDataKey(Supplier<String> name) { this.name = name; }
    public static <T> RenderStateDataKey<T> create(Supplier<String> name) { return new RenderStateDataKey<>(name); }
    public static <T> RenderStateDataKey<T> create() { return new RenderStateDataKey<>(() -> "asterion:render_state"); }
    @Override public String toString() { return name.get(); }
}
