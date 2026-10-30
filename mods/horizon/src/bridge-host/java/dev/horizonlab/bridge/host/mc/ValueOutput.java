package dev.horizonlab.bridge.host.mc;

public interface ValueOutput {
    void putInt(String key, int value);

    void putLong(String key, long value);

    void putBoolean(String key, boolean value);
}
