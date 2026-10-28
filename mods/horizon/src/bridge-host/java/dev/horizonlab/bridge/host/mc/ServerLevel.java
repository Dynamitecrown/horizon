package dev.horizonlab.bridge.host.mc;

import java.util.LinkedHashMap;
import java.util.Map;

/** The bridged block entities of one dimension, kept for Horizon's world. */
public class ServerLevel extends Level {
    public final Map<BlockPos, BlockEntity> blockEntities = new LinkedHashMap<>();
    private static final RegistryAccess REGISTRIES = new RegistryAccess() {};

    public BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }

    public RegistryAccess registryAccess() { return REGISTRIES; }
}
