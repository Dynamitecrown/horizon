package dev.horizonlab.bridge.host.mc;

import dev.horizonlab.bridge.host.brig.ArgumentType;
import dev.horizonlab.bridge.host.brig.LiteralArgumentBuilder;
import dev.horizonlab.bridge.host.brig.RequiredArgumentBuilder;

public final class Commands {
    private Commands() {}

    public static final PermissionCheck LEVEL_GAMEMASTERS = new PermissionCheck() {};

    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) { return new LiteralArgumentBuilder<>(name); }

    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
        return new RequiredArgumentBuilder<>(name, type);
    }

    public static PermissionProviderCheck hasPermission(PermissionCheck check) { return new PermissionProviderCheck(check); }
}
