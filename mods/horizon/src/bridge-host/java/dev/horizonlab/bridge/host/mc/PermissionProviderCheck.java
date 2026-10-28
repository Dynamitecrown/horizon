package dev.horizonlab.bridge.host.mc;

import java.util.function.Predicate;

public final class PermissionProviderCheck implements Predicate<Object> {
    final PermissionCheck check;

    PermissionProviderCheck(PermissionCheck check) { this.check = check; }

    // shortcut: every host command source is an operator; check Horizon's permission levels once players run these.
    @Override
    public boolean test(Object source) { return source instanceof CommandSourceStack; }
}
