package dev.horizonlab.bridge.host.mojang;

import dev.horizonlab.bridge.host.Host;
import dev.horizonlab.bridge.host.slf4j.Logger;

public final class LogUtils {
    private LogUtils() {}

    /** Collects the mod's log lines (the format's two {} filled in) for Horizon's game log. */
    public static Logger getLogger() {
        return (format, first, second) -> Host.LOG.add(
                format.replaceFirst("\\{}", String.valueOf(first)).replaceFirst("\\{}", String.valueOf(second)));
    }
}
