package dev.horizonlab.bridge.host.brig;

import java.util.List;

/** An argument: how many words it takes and how they parse. */
public interface ArgumentType<T> {
    int width();

    T parse(List<String> words);
}
