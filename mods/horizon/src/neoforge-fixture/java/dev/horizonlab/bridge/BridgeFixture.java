package dev.horizonlab.bridge;

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

@Mod(BridgeFixture.ID)
public final class BridgeFixture {
    public static final String ID = "horizon_bridge_fixture";
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredBlock<CounterBlock> COUNTER = BLOCKS.registerBlock(
            "counter", CounterBlock::new, properties -> properties.strength(1.5f));
    public static final Supplier<BlockEntityType<CounterEntity>> COUNTER_ENTITY = TYPES.register(
            "counter", () -> new BlockEntityType<>(CounterEntity::new, false, COUNTER.get()));
    private static final SimpleCommandExceptionType NOT_COUNTER = new SimpleCommandExceptionType(
            Component.literal("Expected a loaded Horizon counter block"));

    public BridgeFixture(IEventBus bus) {
        ITEMS.registerSimpleBlockItem("counter", COUNTER);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        NeoForge.EVENT_BUS.addListener(BridgeFixture::commands);
    }

    private static void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("bridgefixture")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        for (String action : new String[]{"state", "reset", "pause", "resume", "interact", "verify"}) {
            root.then(Commands.literal(action).then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> {
                        var source = context.getSource();
                        CounterEntity entity = counter(source.getLevel(),
                                BlockPosArgument.getLoadedBlockPos(context, "pos"));
                        switch (action) {
                            case "reset" -> entity.counter = new CounterState(0, 0, 0, false);
                            case "pause" -> entity.counter.paused = true;
                            case "resume" -> entity.counter.paused = false;
                            case "interact" -> entity.counter.interact();
                            case "verify" -> {
                                var registries = source.getLevel().registryAccess();
                                var restored = BlockEntity.loadStatic(entity.getBlockPos(), entity.getBlockState(),
                                        entity.saveWithFullMetadata(registries), registries);
                                if (!(restored instanceof CounterEntity copy)
                                        || !entity.counter.json().equals(copy.counter.json())) {
                                    throw new SimpleCommandExceptionType(
                                            Component.literal("Counter persistence round-trip failed")).create();
                                }
                            }
                            default -> { }
                        }
                        if (!action.equals("state") && !action.equals("verify")) entity.setChanged();
                        String result = entity.counter.json();
                        source.sendSuccess(() -> Component.literal(result), false);
                        return 1;
                    })));
        }
        root.then(Commands.literal("trace").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 200))
                        .executes(context -> {
                            CounterEntity entity = counter(context.getSource().getLevel(),
                                    BlockPosArgument.getLoadedBlockPos(context, "pos"));
                            entity.trace = IntegerArgumentType.getInteger(context, "ticks");
                            context.getSource().sendSuccess(() -> Component.literal("Trace armed"), false);
                            return 1;
                        }))));
        event.getDispatcher().register(root);
    }

    private static CounterEntity counter(ServerLevel level, BlockPos pos) throws CommandSyntaxException {
        if (level.getBlockEntity(pos) instanceof CounterEntity entity) return entity;
        throw NOT_COUNTER.create();
    }

    public static final class CounterBlock extends Block implements EntityBlock {
        public CounterBlock(BlockBehaviour.Properties properties) { super(properties); }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new CounterEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
                Level level, BlockState state, BlockEntityType<T> type) {
            if (!(level instanceof ServerLevel) || type != COUNTER_ENTITY.get()) return null;
            return (world, pos, blockState, entity) -> ((CounterEntity) entity).tick();
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel server && server.getBlockEntity(pos) instanceof CounterEntity entity) {
                entity.counter.interact();
                entity.setChanged();
                player.sendSystemMessage(Component.literal(entity.counter.json()));
            }
            return InteractionResult.SUCCESS;
        }
    }

    public static final class CounterEntity extends BlockEntity {
        private CounterState counter = new CounterState(0, 0, 0, false);
        private int trace;

        public CounterEntity(BlockPos pos, BlockState state) {
            super(COUNTER_ENTITY.get(), pos, state);
        }

        private void tick() {
            if (!counter.paused) {
                counter.tick();
                setChanged();
            }
            if (trace > 0) {
                trace--;
                LOGGER.info("BRIDGE_FIXTURE {} {}", getBlockPos().toShortString(), counter.json());
            }
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putInt("fixture_schema", 1);
            output.putInt("value", counter.value);
            output.putLong("ticks", counter.ticks);
            output.putInt("interactions", counter.interactions);
            output.putBoolean("paused", counter.paused);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            if (input.getIntOr("fixture_schema", 1) != 1) {
                throw new IllegalArgumentException("Unsupported Horizon fixture state version");
            }
            counter = new CounterState(input.getIntOr("value", 0), input.getLongOr("ticks", 0),
                    input.getIntOr("interactions", 0), input.getBooleanOr("paused", false));
        }
    }
}
