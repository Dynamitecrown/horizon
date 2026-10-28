package dev.horizonlab.bridge.host.brig;

public class LiteralArgumentBuilder<S> extends ArgumentBuilder<S, LiteralArgumentBuilder<S>> {
    public final String literal;

    public LiteralArgumentBuilder(String literal) { this.literal = literal; }

    @Override
    protected LiteralArgumentBuilder<S> getThis() { return this; }
}
