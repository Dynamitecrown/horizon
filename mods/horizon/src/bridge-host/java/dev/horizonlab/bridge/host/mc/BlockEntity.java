package dev.horizonlab.bridge.host.mc;

import dev.horizonlab.bridge.host.Host;

public abstract class BlockEntity {
    private final BlockEntityType<?> type;
    private final BlockPos pos;
    private final BlockState state;
    /** Set by {@link #setChanged()}: Horizon saves the chunk's data again. */
    public boolean changed;

    public BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        this.type = type;
        this.pos = pos;
        this.state = state;
    }

    public BlockEntityType<?> getType() { return type; }

    public BlockPos getBlockPos() { return pos; }

    public BlockState getBlockState() { return state; }

    public void setChanged() { changed = true; }

    protected void saveAdditional(ValueOutput output) {}

    protected void loadAdditional(ValueInput input) {}

    /** The saved form Horizon keeps: the type id and what {@link #saveAdditional} wrote. */
    public CompoundTag saveWithFullMetadata(HolderLookup.Provider registries) {
        var tag = new CompoundTag(Host.blockEntityTypeId(type));
        saveAdditional(tag.new Output());
        return tag;
    }

    /** A fresh block entity of the saved type, loaded through {@link #loadAdditional}. */
    public static BlockEntity loadStatic(BlockPos pos, BlockState state, CompoundTag tag, HolderLookup.Provider registries) {
        BlockEntityType<?> type = Host.blockEntityType(tag.id);
        BlockEntity entity = type.factory.create(pos, state);
        entity.loadAdditional(tag.new Input());
        return entity;
    }
}
