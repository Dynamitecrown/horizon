package dev.horizonlab.bridge.host.mc;

public interface ValueInput {
    int getIntOr(String key, int fallback);

    long getLongOr(String key, long fallback);

    boolean getBooleanOr(String key, boolean fallback);
}
