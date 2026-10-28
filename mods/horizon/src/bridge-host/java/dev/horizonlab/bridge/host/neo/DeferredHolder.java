package dev.horizonlab.bridge.host.neo;

import java.util.function.Supplier;

public class DeferredHolder<R, T> implements Supplier<T> {
    final String id;
    T value;

    DeferredHolder(String id) { this.id = id; }

    @Override
    public T get() {
        if (value == null) throw new IllegalStateException("Registry object not registered yet: " + id);
        return value;
    }
}
