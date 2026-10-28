package dev.horizonlab.bridge.host.mc;

public abstract class BlockBehaviour {
    final Properties properties;

    protected BlockBehaviour(Properties properties) { this.properties = properties; }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    public static final class Properties {
        public final String id;
        public float destroyTime;
        public float explosionResistance;

        public Properties(String id) { this.id = id; }

        public Properties strength(float strength) {
            destroyTime = strength;
            explosionResistance = strength;
            return this;
        }
    }
}
