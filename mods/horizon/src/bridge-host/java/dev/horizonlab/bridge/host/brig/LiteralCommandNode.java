package dev.horizonlab.bridge.host.brig;

public class LiteralCommandNode<S> {
    public final LiteralArgumentBuilder<S> builder;

    LiteralCommandNode(LiteralArgumentBuilder<S> builder) { this.builder = builder; }
}
