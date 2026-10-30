package dev.horizonlab.bridge;

public final class CounterStateTest {
    public static void main(String[] args) {
        CounterState state = new CounterState(0, 0, 0, false);
        for (int i = 0; i < 3; i++) state.interact();
        assert state.value == 9 && state.interactions == 3;
        for (int i = 0; i < 19; i++) state.tick();
        assert state.value == 9 && state.ticks == 19;
        state.tick();
        assert state.value == 11 && state.ticks == 20;
        for (int i = 20; i < 100; i++) state.tick();
        assert state.value == 19 && state.ticks == 100;
        state.paused = true;
        for (int i = 0; i < 100; i++) state.tick();
        assert state.value == 19 && state.ticks == 100;
        CounterState restored = new CounterState(state.value, state.ticks, state.interactions, state.paused);
        assert restored.json().equals(state.json());
        restored.paused = false;
        for (int i = 0; i < 20; i++) restored.tick();
        assert restored.value == 21 && state.value == 19;
        try { new CounterState(-1, 0, 0, false); throw new AssertionError("Negative state accepted"); }
        catch (IllegalArgumentException expected) { }
        CounterState full = new CounterState(Integer.MAX_VALUE, 19, 0, false);
        try { full.interact(); throw new AssertionError("Overflow accepted"); }
        catch (ArithmeticException expected) { }
        assert full.value == Integer.MAX_VALUE && full.interactions == 0;
        try { full.tick(); throw new AssertionError("Tick overflow accepted"); }
        catch (ArithmeticException expected) { }
        assert full.ticks == 19 && full.value == Integer.MAX_VALUE;
        assert state.json().equals("{\"schema\":1,\"value\":19,\"ticks\":100,\"interactions\":3,\"paused\":true}");
        System.out.println("CounterState checks passed: interactions, boundaries, 100 ticks, pause/resume, restored state, isolation, invalid state, atomic overflow, JSON.");
    }
}
