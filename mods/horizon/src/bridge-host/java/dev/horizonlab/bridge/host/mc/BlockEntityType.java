package dev.horizonlab.bridge.host.mc;

import java.util.List;

public class BlockEntityType<T extends BlockEntity> {
    public final BlockEntitySupplier<? extends T> factory;
    public final List<Block> validBlocks;

    public BlockEntityType(BlockEntitySupplier<? extends T> factory, boolean onlyOpCanSetNbt, Block... validBlocks) {
        this.factory = factory;
        this.validBlocks = List.of(validBlocks);
    }

    @FunctionalInterface
    public interface BlockEntitySupplier<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }
}
