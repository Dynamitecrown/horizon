package dev.horizonlab.bridge.host.mc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Who runs a command: an operator in one dimension. Feedback is kept for Horizon to show. */
public class CommandSourceStack {
    private final ServerLevel level;
    public final List<String> feedback = new ArrayList<>();

    public CommandSourceStack(ServerLevel level) { this.level = level; }

    public ServerLevel getLevel() { return level; }

    public void sendSuccess(Supplier<Component> message, boolean broadcast) { feedback.add(message.get().text()); }
}
