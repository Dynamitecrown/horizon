package dev.horizonlab.bridge.spike;

import dev.horizonlab.bridge.CounterState;

/**
 * M0 spike (docs/bridge/m0-spike.md): the reference scenario as a plain main, so any Java runtime can
 * run the fixture's logic and its output can be compared with the NeoForge trace line by line.
 * Prints 100 tick states, then the paused and resumed states.
 */
public final class TraceMain {
    private TraceMain() {}

    public static void main(String[] args) {
        var counter = new CounterState(0, 0, 0, false);
        for (int i = 0; i < 3; i++) counter.interact();
        for (int i = 0; i < 100; i++) {
            counter.tick();
            System.out.println(counter.json());
        }
        counter.paused = true;
        for (int i = 0; i < 20; i++) counter.tick();
        System.out.println(counter.json());
        var restored = new CounterState(counter.value, counter.ticks, counter.interactions, false);
        for (int i = 0; i < 20; i++) restored.tick();
        System.out.println(restored.json());
    }
}
