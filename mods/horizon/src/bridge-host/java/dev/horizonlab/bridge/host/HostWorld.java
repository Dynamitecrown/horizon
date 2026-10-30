package dev.horizonlab.bridge.host;

import dev.horizonlab.bridge.host.brig.CommandDispatcher;
import dev.horizonlab.bridge.host.mc.Access;
import dev.horizonlab.bridge.host.mc.Block;
import dev.horizonlab.bridge.host.mc.BlockEntity;
import dev.horizonlab.bridge.host.mc.BlockEntityTicker;
import dev.horizonlab.bridge.host.mc.BlockPos;
import dev.horizonlab.bridge.host.mc.BlockState;
import dev.horizonlab.bridge.host.mc.CommandSourceStack;
import dev.horizonlab.bridge.host.mc.CompoundTag;
import dev.horizonlab.bridge.host.mc.EntityBlock;
import dev.horizonlab.bridge.host.mc.Player;
import dev.horizonlab.bridge.host.mc.ServerLevel;
import dev.horizonlab.bridge.host.neo.IEventBus;
import dev.horizonlab.bridge.host.neo.NeoForge;
import dev.horizonlab.bridge.host.neo.RegisterCommandsEvent;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The world the host drives from Rust (M1 step 2, docs/bridge/m1-design.md): one dimension of bridged
 * block entities, their tickers, the mods' commands and one player. Primitives and strings only cross
 * to Rust. In Horizon's game (step 3) the block entities live here, keyed by position, and Horizon's chunks
 * hold their saved form; every entry point is synchronized, since Horizon calls in from more than one thread.
 */
public final class HostWorld {
    private HostWorld() {}

    private static final ServerLevel LEVEL = new ServerLevel();
    private static final CommandDispatcher<CommandSourceStack> COMMANDS = new CommandDispatcher<>();
    private static final Map<BlockPos, BlockEntityTicker<BlockEntity>> TICKERS = new LinkedHashMap<>();
    private static final Player PLAYER = new Player();

    /** Loads one mod and returns what it registered. */
    public static synchronized String load(String jar, String modId) throws Exception {
        int before = Host.REGISTRATIONS.size();
        BridgeLoader.load(Path.of(jar), modId, HostWorld.class.getClassLoader());
        return String.join("; ", Host.REGISTRATIONS.subList(before, Host.REGISTRATIONS.size()));
    }

    /** Server start: the mods register their commands. */
    public static synchronized void start() {
        ((IEventBus.GameBus) NeoForge.EVENT_BUS).post(new RegisterCommandsEvent(COMMANDS));
    }

    public static synchronized void place(String blockId, int x, int y, int z) {
        Block block = Host.BLOCKS.get(blockId);
        if (block == null) throw new IllegalArgumentException("Unknown block " + blockId);
        place(block, new BlockPos(x, y, z));
    }

    /** The block entity type id of a block's entity, or "" when it has none. */
    public static synchronized String typeOf(String blockId) {
        Block block = Host.BLOCKS.get(blockId);
        if (!(block instanceof EntityBlock entityBlock)) return "";
        BlockEntity entity = entityBlock.newBlockEntity(new BlockPos(0, 0, 0), new BlockState(block));
        return entity == null ? "" : Host.blockEntityTypeId(entity.getType());
    }

    /** Recreates a block entity Horizon read from a save: its type's block, then its saved form. */
    public static synchronized void load(int x, int y, int z, String saved) {
        Block block = Host.blockEntityType(CompoundTag.fromJson(saved).id).validBlocks.get(0);
        place(block, new BlockPos(x, y, z));
        restore(x, y, z, saved);
    }

    /** One server tick of the block entity at the position, when its block has a ticker. */
    public static synchronized void tickAt(int x, int y, int z) {
        var pos = new BlockPos(x, y, z);
        BlockEntityTicker<BlockEntity> ticker = TICKERS.get(pos);
        BlockEntity entity = LEVEL.getBlockEntity(pos);
        if (ticker != null && entity != null) ticker.tick(LEVEL, pos, entity.getBlockState(), entity);
    }

    /** Whether the line's first word is one of the mods' command roots. */
    public static synchronized boolean handles(String line) {
        String root = line.trim().split("\\s+", 2)[0];
        return COMMANDS.roots.stream().anyMatch(node -> node.builder.literal.equals(root));
    }

    @SuppressWarnings("unchecked")
    private static void place(Block block, BlockPos pos) {
        var state = new BlockState(block);
        if (block instanceof EntityBlock entityBlock) {
            BlockEntity entity = entityBlock.newBlockEntity(pos, state);
            LEVEL.blockEntities.put(pos, entity);
            TICKERS.remove(pos);
            var ticker = entityBlock.getTicker(LEVEL, state, entity.getType());
            if (ticker != null) TICKERS.put(pos, (BlockEntityTicker<BlockEntity>) ticker);
        }
    }

    /** Runs a command as an operator; returns its feedback lines. */
    public static synchronized String command(String line) throws Exception {
        var source = new CommandSourceStack(LEVEL);
        COMMANDS.execute(line, source);
        return String.join("\n", source.feedback);
    }

    /** One server tick of every ticking bridged block entity, in placement order. */
    public static synchronized void tick() {
        TICKERS.forEach((pos, ticker) -> {
            BlockEntity entity = LEVEL.getBlockEntity(pos);
            ticker.tick(LEVEL, pos, entity.getBlockState(), entity);
        });
    }

    /** An empty-hand right-click; returns the messages the player got. */
    public static synchronized String use(int x, int y, int z) {
        BlockEntity entity = entity(x, y, z);
        int before = PLAYER.messages.size();
        Access.useWithoutItem(entity.getBlockState().block, entity.getBlockState(), LEVEL, entity.getBlockPos(), PLAYER);
        return String.join("\n", PLAYER.messages.subList(before, PLAYER.messages.size()));
    }

    /** The block entity's saved form, as Horizon would store it. */
    public static synchronized String save(int x, int y, int z) {
        return entity(x, y, z).saveWithFullMetadata(LEVEL.registryAccess()).toJson();
    }

    /** Replaces the block entity with one loaded from its saved form. */
    public static synchronized void restore(int x, int y, int z, String saved) {
        BlockEntity old = entity(x, y, z);
        var pos = old.getBlockPos();
        LEVEL.blockEntities.put(pos, BlockEntity.loadStatic(pos, old.getBlockState(), CompoundTag.fromJson(saved),
                LEVEL.registryAccess()));
    }

    /** The mods' log lines since the last call. */
    public static synchronized String drainLog() {
        String lines = String.join("\n", Host.LOG);
        Host.LOG.clear();
        return lines;
    }

    private static BlockEntity entity(int x, int y, int z) {
        BlockEntity entity = LEVEL.getBlockEntity(new BlockPos(x, y, z));
        if (entity == null) throw new IllegalArgumentException("No bridged block entity at " + x + " " + y + " " + z);
        return entity;
    }
}
