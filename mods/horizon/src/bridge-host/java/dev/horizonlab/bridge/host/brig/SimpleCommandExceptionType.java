package dev.horizonlab.bridge.host.brig;

public class SimpleCommandExceptionType {
    private final Message message;

    public SimpleCommandExceptionType(Message message) { this.message = message; }

    public CommandSyntaxException create() { return new CommandSyntaxException(message); }
}
