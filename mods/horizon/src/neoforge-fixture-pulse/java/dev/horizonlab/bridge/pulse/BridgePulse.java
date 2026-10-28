package dev.horizonlab.bridge.pulse;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.util.function.Supplier;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BridgePulse.ID)
public final class BridgePulse {
    public static final String ID = "horizon_bridge_pulse";
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredBlock<PulseBlock> PULSE = BLOCKS.registerBlock(
            "pulse", PulseBlock::new, properties -> properties.strength(1.5f));
    public static final Supplier<BlockEntityType<PulseEntity>> PULSE_ENTITY = TYPES.register(
            "pulse", () -> new BlockEntityType<>(PulseEntity::new, false, PULSE.get()));
    private static final SimpleCommandExceptionType NOT_PULSE = new SimpleCommandExceptionType(
            Component.literal("Expected a loaded Horizon pulse block"));

    public BridgePulse(IEventBus bus) {
        ITEMS.registerSimpleBlockItem("pulse", PULSE);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        NeoForge.EVENT_BUS.addListener(BridgePulse::commands);
    }

    private static void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("bridgepulse")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        for (String action : new String[]{"state", "reset", "pause", "resume", "interact", "verify"}) {
            root.then(Commands.literal(action).then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> {
                        var source = context.getSource();
                        PulseEntity entity = pulseAt(source.getLevel(),
                                BlockPosArgument.getLoadedBlockPos(context, "pos"));
                        switch (action) {
                            case "reset" -> entity.pulse = new PulseState(0, 0, 0, false, false);
                            case "pause" -> entity.pulse.paused = true;
                            case "resume" -> entity.pulse.paused = false;
                            case "interact" -> entity.pulse.interact();
                            case "verify" -> {
                                var registries = source.getLevel().registryAccess();
                                var restored = BlockEntity.loadStatic(entity.getBlockPos(), entity.getBlockState(),
                                        entity.saveWithFullMetadata(registries), registries);
                                if (!(restored instanceof PulseEntity copy)
                                        || !entity.pulse.json().equals(copy.pulse.json())) {
                                    throw new SimpleCommandExceptionType(
                                            Component.literal("Pulse persistence round-trip failed")).create();
                                }
                            }
                            default -> { }
                        }
                        if (!action.equals("state") && !action.equals("verify")) entity.setChanged();
                        String result = entity.pulse.json();
                        source.sendSuccess(() -> Component.literal(result), false);
                        return 1;
                    })));
        }
        root.then(Commands.literal("trace").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 200))
                        .executes(context -> {
                            PulseEntity entity = pulseAt(context.getSource().getLevel(),
                                    BlockPosArgument.getLoadedBlockPos(context, "pos"));
                            entity.trace = IntegerArgumentType.getInteger(context, "ticks");
                            context.getSource().sendSuccess(() -> Component.literal("Trace armed"), false);
                            return 1;
                        }))));
        event.getDispatcher().register(root);
    }

    private static PulseEntity pulseAt(ServerLevel level, BlockPos pos) throws CommandSyntaxException {
        if (level.getBlockEntity(pos) instanceof PulseEntity entity) return entity;
        throw NOT_PULSE.create();
    }

    public static final class PulseBlock extends Block implements EntityBlock {
        public PulseBlock(BlockBehaviour.Properties properties) { super(properties); }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new PulseEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
                Level level, BlockState state, BlockEntityType<T> type) {
            if (!(level instanceof ServerLevel) || type != PULSE_ENTITY.get()) return null;
            return (world, pos, blockState, entity) -> ((PulseEntity) entity).tick();
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel server && server.getBlockEntity(pos) instanceof PulseEntity entity) {
                entity.pulse.interact();
                entity.setChanged();
                player.sendSystemMessage(Component.literal(entity.pulse.json()));
            }
            return InteractionResult.SUCCESS;
        }
    }

    public static final class PulseEntity extends BlockEntity {
        private PulseState pulse = new PulseState(0, 0, 0, false, false);
        private int trace;

        public PulseEntity(BlockPos pos, BlockState state) {
            super(PULSE_ENTITY.get(), pos, state);
        }

        private void tick() {
            if (!pulse.paused) {
                pulse.tick();
                setChanged();
            }
            if (trace > 0) {
                trace--;
                LOGGER.info("BRIDGE_PULSE {} {}", getBlockPos().toShortString(), pulse.json());
            }
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putInt("pulse_schema", 1);
            output.putInt("level", pulse.level);
            output.putLong("ticks", pulse.ticks);
            output.putInt("interactions", pulse.interactions);
            output.putBoolean("charged", pulse.charged);
            output.putBoolean("paused", pulse.paused);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            if (input.getIntOr("pulse_schema", 1) != 1) {
                throw new IllegalArgumentException("Unsupported Horizon fixture state version");
            }
            pulse = new PulseState(input.getIntOr("level", 0), input.getLongOr("ticks", 0),
                    input.getIntOr("interactions", 0), input.getBooleanOr("charged", false),
                    input.getBooleanOr("paused", false));
        }
    }
}
