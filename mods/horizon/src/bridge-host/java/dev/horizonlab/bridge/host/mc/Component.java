package dev.horizonlab.bridge.host.mc;

import dev.horizonlab.bridge.host.brig.Message;

public interface Component extends Message {
    String text();

    static MutableComponent literal(String text) { return new MutableComponent(text); }
}
