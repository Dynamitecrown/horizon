package dev.horizonlab.bridge.fault;

import java.util.function.Supplier;
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
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Fixture 3: a ticking block entity that, on its fifth tick, reads the game time with
 * {@code Level.getGameTime()}, a member the bridge does not supply yet. On NeoForge it just works;
 * through the bridge it must fail with an error naming that member, and saves must stay intact.
 */
@Mod(BridgeFault.ID)
public final class BridgeFault {
    public static final String ID = "horizon_bridge_fault";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredBlock<FaultBlock> FAULT = BLOCKS.registerBlock(
            "fault", FaultBlock::new, properties -> properties.strength(1.5f));
    public static final Supplier<BlockEntityType<FaultEntity>> FAULT_ENTITY = TYPES.register(
            "fault", () -> new BlockEntityType<>(FaultEntity::new, false, FAULT.get()));

    public BridgeFault(IEventBus bus) {
        ITEMS.registerSimpleBlockItem("fault", FAULT);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
    }

    public static final class FaultBlock extends Block implements EntityBlock {
        public FaultBlock(BlockBehaviour.Properties properties) { super(properties); }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new FaultEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
                Level level, BlockState state, BlockEntityType<T> type) {
            if (!(level instanceof ServerLevel) || type != FAULT_ENTITY.get()) return null;
            return (world, pos, blockState, entity) -> {
                FaultEntity fault = (FaultEntity) entity;
                if (fault.state.tick()) fault.state.gameTime = world.getGameTime();
                fault.setChanged();
            };
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel server && server.getBlockEntity(pos) instanceof FaultEntity entity) {
                player.sendSystemMessage(Component.literal(entity.state.json()));
            }
            return InteractionResult.SUCCESS;
        }
    }

    public static final class FaultEntity extends BlockEntity {
        private FaultState state = new FaultState(0, -1);

        public FaultEntity(BlockPos pos, BlockState state) {
            super(FAULT_ENTITY.get(), pos, state);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putInt("fault_schema", 1);
            output.putLong("ticks", state.ticks);
            output.putLong("game_time", state.gameTime);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            state = new FaultState(input.getLongOr("ticks", 0), input.getLongOr("game_time", -1));
        }
    }
}
