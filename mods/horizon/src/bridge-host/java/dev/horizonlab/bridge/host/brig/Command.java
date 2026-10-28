package dev.horizonlab.bridge.host.brig;

@FunctionalInterface
public interface Command<S> {
    int run(CommandContext<S> context) throws CommandSyntaxException;
}
