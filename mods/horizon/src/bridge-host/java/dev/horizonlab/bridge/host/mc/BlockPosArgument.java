package dev.horizonlab.bridge.host.mc;

import dev.horizonlab.bridge.host.brig.ArgumentType;
import dev.horizonlab.bridge.host.brig.CommandContext;
import dev.horizonlab.bridge.host.brig.CommandSyntaxException;
import java.util.List;

/** Three integer coordinates (relative ~ and local ^ forms are not supported yet). */
public final class BlockPosArgument implements ArgumentType<BlockPos> {
    public static BlockPosArgument blockPos() { return new BlockPosArgument(); }

    @Override
    public int width() { return 3; }

    @Override
    public BlockPos parse(List<String> words) {
        return new BlockPos(Integer.parseInt(words.get(0)), Integer.parseInt(words.get(1)), Integer.parseInt(words.get(2)));
    }

    // shortcut: every position counts as loaded; check Horizon's loaded chunks once this runs in the game.
    public static BlockPos getLoadedBlockPos(CommandContext<CommandSourceStack> context, String name)
            throws CommandSyntaxException {
        return context.getArgument(name, BlockPos.class);
    }
}
