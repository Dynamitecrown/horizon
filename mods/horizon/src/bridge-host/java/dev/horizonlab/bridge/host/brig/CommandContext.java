package dev.horizonlab.bridge.host.brig;

import java.util.Map;

public class CommandContext<S> {
    private final S source;
    private final Map<String, Object> arguments;

    CommandContext(S source, Map<String, Object> arguments) {
        this.source = source;
        this.arguments = arguments;
    }

    public S getSource() { return source; }

    public <V> V getArgument(String name, Class<V> type) {
        Object value = arguments.get(name);
        if (!type.isInstance(value)) throw new IllegalArgumentException("No argument " + name + " of type " + type.getSimpleName());
        return type.cast(value);
    }
}
