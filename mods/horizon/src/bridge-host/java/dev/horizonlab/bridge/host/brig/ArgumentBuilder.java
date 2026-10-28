package dev.horizonlab.bridge.host.brig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {
    public final List<ArgumentBuilder<S, ?>> children = new ArrayList<>();
    public Predicate<S> requirement = source -> true;
    public Command<S> command;

    protected abstract T getThis();

    public T then(ArgumentBuilder<S, ?> argument) {
        children.add(argument);
        return getThis();
    }

    public T requires(Predicate<S> requirement) {
        this.requirement = requirement;
        return getThis();
    }

    public T executes(Command<S> command) {
        this.command = command;
        return getThis();
    }
}
