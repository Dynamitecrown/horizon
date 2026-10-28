package dev.horizonlab.bridge.host;

import dev.horizonlab.bridge.host.mc.Block;
import dev.horizonlab.bridge.host.mc.BlockEntityType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bridge's side of loaded mods (docs/bridge/m1-design.md): what the stand-ins recorded, and the
 * error every stand-in member without Horizon behaviour yet throws, naming that member.
 */
public final class Host {
    private Host() {}

    /** Registrations in the order the mods' deferred registers made them: "registry id". */
    public static final List<String> REGISTRATIONS = new ArrayList<>();
    public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    public static final Map<String, BlockEntityType<?>> BLOCK_ENTITY_TYPES = new LinkedHashMap<>();
    /** The mods' log lines, for Horizon's game log. */
    public static final List<String> LOG = new ArrayList<>();

    /** Records a registered object under its registry and id. */
    public static void registered(String registry, String id, Object value) {
        REGISTRATIONS.add(registry + " " + id);
        if (value instanceof Block block) BLOCKS.put(id, block);
        if (value instanceof BlockEntityType<?> type) BLOCK_ENTITY_TYPES.put(id, type);
    }

    public static String blockEntityTypeId(BlockEntityType<?> type) {
        return BLOCK_ENTITY_TYPES.entrySet().stream().filter(e -> e.getValue() == type).map(Map.Entry::getKey)
                .findFirst().orElseThrow(() -> new IllegalStateException("Unregistered block entity type"));
    }

    public static BlockEntityType<?> blockEntityType(String id) {
        BlockEntityType<?> type = BLOCK_ENTITY_TYPES.get(id);
        if (type == null) throw new IllegalArgumentException("Unknown block entity type " + id);
        return type;
    }

    public static UnsupportedOperationException unsupported(String member) {
        return new UnsupportedOperationException("Horizon bridge: not supported yet: " + member);
    }
}
