package dev.horizonlab.bridge.pulse;

/**
 * Original, game-independent Java logic of the second bridge fixture. Its rule differs from the
 * counter's on purpose: interacting toggles a charge, and every 7 running ticks the level rises by
 * 5 per interaction while charged, by 1 otherwise.
 */
public final class PulseState {
    public int level;
    public long ticks;
    public int interactions;
    public boolean charged;
    public boolean paused;

    public PulseState(int level, long ticks, int interactions, boolean charged, boolean paused) {
        if (level < 0 || ticks < 0 || interactions < 0) {
            throw new IllegalArgumentException("Negative pulse state");
        }
        this.level = level;
        this.ticks = ticks;
        this.interactions = interactions;
        this.charged = charged;
        this.paused = paused;
    }

    public void interact() {
        interactions = Math.addExact(interactions, 1);
        charged = !charged;
    }

    public void tick() {
        if (paused) return;
        long nextTicks = Math.addExact(ticks, 1);
        int nextLevel = level;
        if (nextTicks % 7 == 0) {
            nextLevel = Math.addExact(level, charged ? Math.multiplyExact(interactions, 5) : 1);
        }
        ticks = nextTicks;
        level = nextLevel;
    }

    public String json() {
        return "{\"schema\":1,\"level\":" + level + ",\"ticks\":" + ticks + ",\"interactions\":" + interactions
                + ",\"charged\":" + charged + ",\"paused\":" + paused + "}";
    }
}
