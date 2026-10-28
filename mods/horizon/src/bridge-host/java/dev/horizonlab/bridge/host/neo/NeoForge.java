package dev.horizonlab.bridge.host.neo;

public final class NeoForge {
    private NeoForge() {}

    public static final IEventBus EVENT_BUS = new IEventBus.GameBus();
}
