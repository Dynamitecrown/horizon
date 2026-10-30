package dev.horizonlab.bridge.pulse;

public final class PulseStateTest {
    public static void main(String[] args) {
        PulseState state = new PulseState(0, 0, 0, false, false);
        state.interact();
        assert state.charged && state.interactions == 1;
        state.interact();
        state.interact();
        assert state.charged && state.interactions == 3 && state.level == 0;
        for (int i = 0; i < 6; i++) state.tick();
        assert state.level == 0 && state.ticks == 6;
        state.tick();
        assert state.level == 15 && state.ticks == 7;
        for (int i = 7; i < 100; i++) state.tick();
        assert state.level == 210 && state.ticks == 100;
        state.paused = true;
        for (int i = 0; i < 20; i++) state.tick();
        assert state.level == 210 && state.ticks == 100;
        PulseState restored = new PulseState(state.level, state.ticks, state.interactions, state.charged, state.paused);
        assert restored.json().equals(state.json());
        restored.paused = false;
        for (int i = 0; i < 20; i++) restored.tick();
        assert restored.level == 255 && restored.ticks == 120 && state.level == 210;
        PulseState uncharged = new PulseState(0, 0, 2, false, false);
        for (int i = 0; i < 14; i++) uncharged.tick();
        assert uncharged.level == 2;
        try { new PulseState(-1, 0, 0, false, false); throw new AssertionError("Negative state accepted"); }
        catch (IllegalArgumentException expected) { }
        PulseState full = new PulseState(Integer.MAX_VALUE, 6, 1, true, false);
        try { full.tick(); throw new AssertionError("Overflow accepted"); }
        catch (ArithmeticException expected) { }
        assert full.ticks == 6 && full.level == Integer.MAX_VALUE;
        assert state.json().equals(
                "{\"schema\":1,\"level\":210,\"ticks\":100,\"interactions\":3,\"charged\":true,\"paused\":true}");
        System.out.println("PulseState checks passed: toggling, 7-tick pulses, charged and uncharged rules, 100 ticks, pause/resume, restored state, invalid state, atomic overflow, JSON.");
    }
}
