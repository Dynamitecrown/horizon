package dev.horizonlab.bridge.host.brig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The registered command trees, and running one line against them (literals and fixed-width arguments). */
public class CommandDispatcher<S> {
    public final List<LiteralCommandNode<S>> roots = new ArrayList<>();

    public LiteralCommandNode<S> register(LiteralArgumentBuilder<S> command) {
        var node = new LiteralCommandNode<>(command);
        roots.add(node);
        return node;
    }

    /** Runs a command line without the leading slash; returns the command's result. */
    public int execute(String line, S source) throws CommandSyntaxException {
        List<String> words = Arrays.asList(line.trim().split("\\s+"));
        Map<String, Object> arguments = new LinkedHashMap<>();
        List<? extends ArgumentBuilder<S, ?>> options = roots.stream().map(root -> root.builder).toList();
        ArgumentBuilder<S, ?> node = null;
        int at = 0;
        while (at < words.size()) {
            ArgumentBuilder<S, ?> next = null;
            for (ArgumentBuilder<S, ?> option : options) {
                if (option instanceof LiteralArgumentBuilder<?> literal && literal.literal.equals(words.get(at))) {
                    next = option;
                    at += 1;
                    break;
                }
                if (option instanceof RequiredArgumentBuilder<?, ?> argument
                        && at + argument.type.width() <= words.size()) {
                    arguments.put(argument.name, argument.type.parse(words.subList(at, at + argument.type.width())));
                    next = option;
                    at += argument.type.width();
                    break;
                }
            }
            if (next == null || !next.requirement.test(source)) throw error("Unknown or incomplete command: " + line);
            node = next;
            options = node.children;
        }
        if (node == null || node.command == null) throw error("Unknown or incomplete command: " + line);
        return node.command.run(new CommandContext<>(source, arguments));
    }

    private static CommandSyntaxException error(String text) {
        return new CommandSyntaxException(new Message() {
            @Override
            public String toString() { return text; }
        });
    }
}
