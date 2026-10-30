package dev.horizonlab.bridge.host.neo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public interface IEventBus {
    void addListener(Consumer<?> listener);

    /** A mod's own bus: it holds the deferred registers registered on it. */
    final class ModBus implements IEventBus {
        public final List<DeferredRegister<?>> registers = new ArrayList<>();
        public final List<Consumer<?>> listeners = new ArrayList<>();

        @Override
        public void addListener(Consumer<?> listener) { listeners.add(listener); }
    }

    /** The game bus (NeoForge.EVENT_BUS). */
    final class GameBus implements IEventBus {
        public final List<Consumer<?>> listeners = new ArrayList<>();

        @Override
        public void addListener(Consumer<?> listener) { listeners.add(listener); }

        // shortcut: every listener gets every event, which is right while RegisterCommandsEvent is the only
        // game event the bridge posts; match listeners to event types before posting a second kind.
        @SuppressWarnings("unchecked")
        public void post(Object event) {
            for (Consumer<?> listener : listeners) ((Consumer<Object>) listener).accept(event);
        }
    }
}
