package dev.horizonlab.bridge.host.brig;

import java.util.List;

public final class IntegerArgumentType implements ArgumentType<Integer> {
    public final int minimum;
    public final int maximum;

    private IntegerArgumentType(int minimum, int maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public static IntegerArgumentType integer(int minimum, int maximum) { return new IntegerArgumentType(minimum, maximum); }

    public static int getInteger(CommandContext<?> context, String name) { return context.getArgument(name, Integer.class); }

    @Override
    public int width() { return 1; }

    @Override
    public Integer parse(List<String> words) {
        int value = Integer.parseInt(words.get(0));
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Integer must be between " + minimum + " and " + maximum + ", found " + value);
        }
        return value;
    }
}
