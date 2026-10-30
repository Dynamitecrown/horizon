package dev.horizonlab.bridge.spike;

import dev.horizonlab.bridge.CounterState;

/** M0 spike (docs/bridge/m0-spike.md): Java calling into the Rust host, the shape of a future host adapter. */
public final class HostProbe {
    private HostProbe() {}

    /** Implemented in Rust: receives each counter state. */
    static native void report(String json);

    /** Implemented in Rust: returns value + 1, for the cost of one Java-to-Rust call. */
    static native long echo(long value);

    /** Runs the fixture's own counter logic, reporting every tick to the host; returns the final state. */
    public static String run(int interactions, int ticks) {
        var counter = new CounterState(0, 0, 0, false);
        for (int i = 0; i < interactions; i++) counter.interact();
        for (int i = 0; i < ticks; i++) {
            counter.tick();
            report(counter.json());
        }
        return counter.json();
    }

    public static long echoLoop(int calls) {
        long sum = 0;
        for (int i = 0; i < calls; i++) sum += echo(i);
        return sum;
    }

    /** Where the JVM loaded a class from, to prove the fixture's logic comes from its own JAR. */
    public static String origin(String className) throws ClassNotFoundException {
        var source = Class.forName(className).getProtectionDomain().getCodeSource();
        return source == null ? "unknown" : source.getLocation().toString();
    }
}
