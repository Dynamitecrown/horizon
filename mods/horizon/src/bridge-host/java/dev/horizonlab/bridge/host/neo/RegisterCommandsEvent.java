package dev.horizonlab.bridge.host.neo;

import dev.horizonlab.bridge.host.brig.CommandDispatcher;
import dev.horizonlab.bridge.host.mc.CommandSourceStack;

public class RegisterCommandsEvent {
    private final CommandDispatcher<CommandSourceStack> dispatcher;

    public RegisterCommandsEvent(CommandDispatcher<CommandSourceStack> dispatcher) { this.dispatcher = dispatcher; }

    public CommandDispatcher<CommandSourceStack> getDispatcher() { return dispatcher; }
}
