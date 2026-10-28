package dev.horizonlab.bridge.host.brig;

public class RequiredArgumentBuilder<S, T> extends ArgumentBuilder<S, RequiredArgumentBuilder<S, T>> {
    public final String name;
    public final ArgumentType<T> type;

    public RequiredArgumentBuilder(String name, ArgumentType<T> type) {
        this.name = name;
        this.type = type;
    }

    @Override
    protected RequiredArgumentBuilder<S, T> getThis() { return this; }
}
