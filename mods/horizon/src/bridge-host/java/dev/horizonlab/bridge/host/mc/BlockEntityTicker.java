package dev.horizonlab.bridge.host.mc;

@FunctionalInterface
public interface BlockEntityTicker<T extends BlockEntity> {
    void tick(Level level, BlockPos pos, BlockState state, T entity);
}
