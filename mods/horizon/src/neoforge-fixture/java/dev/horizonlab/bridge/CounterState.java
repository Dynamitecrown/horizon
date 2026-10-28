package dev.horizonlab.bridge;

/** Original, game-independent Java logic retained in the fixture JAR for bridge experiments. */
public final class CounterState {
    public int value;
    public long ticks;
    public int interactions;
    public boolean paused;

    public CounterState(int value, long ticks, int interactions, boolean paused) {
        if (value < 0 || ticks < 0 || interactions < 0) {
            throw new IllegalArgumentException("Negative counter state");
        }
        this.value = value;
        this.ticks = ticks;
        this.interactions = interactions;
        this.paused = paused;
    }

    public void interact() {
        int nextValue = Math.addExact(value, 3);
        int nextInteractions = Math.addExact(interactions, 1);
        value = nextValue;
        interactions = nextInteractions;
    }

    public void tick() {
        if (paused) return;
        long nextTicks = Math.addExact(ticks, 1);
        int nextValue = nextTicks % 20 == 0 ? Math.addExact(value, 2) : value;
        ticks = nextTicks;
        value = nextValue;
    }

    public String json() {
        return "{\"schema\":1,\"value\":" + value + ",\"ticks\":" + ticks
                + ",\"interactions\":" + interactions + ",\"paused\":" + paused + "}";
    }
}
