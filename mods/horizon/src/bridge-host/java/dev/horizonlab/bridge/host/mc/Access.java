package dev.horizonlab.bridge.host.mc;

/** The host's way into protected game methods that mods override (same package). */
public final class Access {
    private Access() {}

    public static InteractionResult useWithoutItem(Block block, BlockState state, Level level, BlockPos pos, Player player) {
        return block.useWithoutItem(state, level, pos, player, new BlockHitResult());
    }
}
