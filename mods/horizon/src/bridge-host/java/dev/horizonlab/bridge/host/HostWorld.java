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
 * to Rust. Horizon's own world replaces this in step 3.
 */
public final class HostWorld {
    private HostWorld() {}

    private static final ServerLevel LEVEL = new ServerLevel();
    private static final CommandDispatcher<CommandSourceStack> COMMANDS = new CommandDispatcher<>();
    private static final Map<BlockPos, BlockEntityTicker<BlockEntity>> TICKERS = new LinkedHashMap<>();
    private static final Player PLAYER = new Player();

    /** Loads one mod and returns what it registered. */
    public static String load(String jar, String modId) throws Exception {
        int before = Host.REGISTRATIONS.size();
        BridgeLoader.load(Path.of(jar), modId, HostWorld.class.getClassLoader());
        return String.join("; ", Host.REGISTRATIONS.subList(before, Host.REGISTRATIONS.size()));
    }

    /** Server start: the mods register their commands. */
    public static void start() {
        ((IEventBus.GameBus) NeoForge.EVENT_BUS).post(new RegisterCommandsEvent(COMMANDS));
    }

    @SuppressWarnings("unchecked")
    public static void place(String blockId, int x, int y, int z) {
        Block block = Host.BLOCKS.get(blockId);
        if (block == null) throw new IllegalArgumentException("Unknown block " + blockId);
        var pos = new BlockPos(x, y, z);
        var state = new BlockState(block);
        if (block instanceof EntityBlock entityBlock) {
            BlockEntity entity = entityBlock.newBlockEntity(pos, state);
            LEVEL.blockEntities.put(pos, entity);
            var ticker = entityBlock.getTicker(LEVEL, state, entity.getType());
            if (ticker != null) TICKERS.put(pos, (BlockEntityTicker<BlockEntity>) ticker);
        }
    }

    /** Runs a command as an operator; returns its feedback lines. */
    public static String command(String line) throws Exception {
        var source = new CommandSourceStack(LEVEL);
        COMMANDS.execute(line, source);
        return String.join("\n", source.feedback);
    }

    /** One server tick of every ticking bridged block entity, in placement order. */
    public static void tick() {
        TICKERS.forEach((pos, ticker) -> {
            BlockEntity entity = LEVEL.getBlockEntity(pos);
            ticker.tick(LEVEL, pos, entity.getBlockState(), entity);
        });
    }

    /** An empty-hand right-click; returns the messages the player got. */
    public static String use(int x, int y, int z) {
        BlockEntity entity = entity(x, y, z);
        int before = PLAYER.messages.size();
        Access.useWithoutItem(entity.getBlockState().block, entity.getBlockState(), LEVEL, entity.getBlockPos(), PLAYER);
        return String.join("\n", PLAYER.messages.subList(before, PLAYER.messages.size()));
    }

    /** The block entity's saved form, as Horizon would store it. */
    public static String save(int x, int y, int z) {
        return entity(x, y, z).saveWithFullMetadata(LEVEL.registryAccess()).toJson();
    }

    /** Replaces the block entity with one loaded from its saved form. */
    public static void restore(int x, int y, int z, String saved) {
        BlockEntity old = entity(x, y, z);
        var pos = old.getBlockPos();
        LEVEL.blockEntities.put(pos, BlockEntity.loadStatic(pos, old.getBlockState(), CompoundTag.fromJson(saved),
                LEVEL.registryAccess()));
    }

    /** The mods' log lines since the last call. */
    public static String drainLog() {
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
