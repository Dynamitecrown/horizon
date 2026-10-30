package dev.horizonlab.bridge.host.mc;

public interface EntityBlock {
    BlockEntity newBlockEntity(BlockPos pos, BlockState state);

    default <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }
}
