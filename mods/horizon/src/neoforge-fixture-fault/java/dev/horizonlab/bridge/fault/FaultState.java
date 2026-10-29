package dev.horizonlab.bridge.fault;

/** The fault fixture's state: running ticks, and the tick on which it asks the game for its time. */
public final class FaultState {
    public static final long ASKS_AT = 5;
    public long ticks;
    /** The game time the fixture read, or -1 before it asked. */
    public long gameTime = -1;

    public FaultState(long ticks, long gameTime) {
        this.ticks = ticks;
        this.gameTime = gameTime;
    }

    /** Counts a tick; true when this is the tick that asks for the game time. */
    public boolean tick() {
        return ++ticks == ASKS_AT;
    }

    public String json() {
        return "{\"schema\":1,\"ticks\":" + ticks + ",\"game_time\":" + gameTime + "}";
    }
}
