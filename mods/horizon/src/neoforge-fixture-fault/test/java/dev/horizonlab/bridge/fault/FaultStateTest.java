package dev.horizonlab.bridge.fault;

public final class FaultStateTest {
    public static void main(String[] args) {
        FaultState state = new FaultState(0, -1);
        for (int i = 1; i < FaultState.ASKS_AT; i++) assert !state.tick();
        assert state.tick() && state.ticks == 5;
        assert !state.tick() && state.ticks == 6;
        assert state.json().equals("{\"schema\":1,\"ticks\":6,\"game_time\":-1}");
        System.out.println("FaultState checks passed");
    }
}
