package net.fabricmc.fabric.api.event;

import java.util.function.Function;

public final class EventFactory {
    private EventFactory() {}

    public static <T> Event<T> createArrayBacked(Class<T> type, Function<T[], T> factory) {
        return new ArrayEvent<>(type, factory);
    }

    private static final class ArrayEvent<T> extends Event<T> {
        private final Class<T> type;
        private final Function<T[], T> factory;
        private final java.util.List<T> listeners = new java.util.ArrayList<>();
        private T invoker;

        ArrayEvent(Class<T> type, Function<T[], T> factory) {
            super(listener -> {});
            this.type = type;
            this.factory = factory;
            refresh();
        }

        @SuppressWarnings("unchecked")
        private void refresh() {
            invoker =
                    factory.apply(
                            listeners.toArray(
                                    size -> (T[]) java.lang.reflect.Array.newInstance(type, size)));
        }

        @Override
        public void register(T listener) {
            listeners.add(java.util.Objects.requireNonNull(listener));
            refresh();
        }

        @Override
        public T invoker() {
            return invoker;
        }
    }
}
