package dev.horizonlab.bridge.host.neo;

import dev.horizonlab.bridge.host.Host;
import dev.horizonlab.bridge.host.mc.Block;
import dev.horizonlab.bridge.host.mc.BlockBehaviour;
import dev.horizonlab.bridge.host.mc.ResourceKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Registrations wait until the loader runs them in registry order, as NeoForge's RegisterEvent does. */
public class DeferredRegister<T> {
    final String registry;
    final String namespace;
    private final List<Runnable> pending = new ArrayList<>();
    private boolean registered;

    DeferredRegister(String registry, String namespace) {
        this.registry = registry;
        this.namespace = namespace;
    }

    public static DeferredRegister<?> create(ResourceKey<?> registry, String namespace) {
        return new DeferredRegister<>(registry.path(), namespace);
    }

    public static Blocks createBlocks(String namespace) { return new Blocks(namespace); }

    public static Items createItems(String namespace) { return new Items(namespace); }

    public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> supplier) {
        var holder = new DeferredHolder<T, I>(namespace + ":" + name);
        add(holder, supplier);
        return holder;
    }

    <I> void add(DeferredHolder<?, I> holder, Supplier<? extends I> supplier) {
        if (registered) throw new IllegalStateException("Registration after the register event: " + holder.id);
        pending.add(() -> {
            holder.value = supplier.get();
            Host.registered(registry, holder.id, holder.value);
        });
    }

    public void register(IEventBus bus) {
        if (!(bus instanceof IEventBus.ModBus modBus)) throw new IllegalArgumentException("Not a mod event bus");
        modBus.registers.add(this);
    }

    /** Runs this register's entries if it is for {@code registry} (called by the loader). */
    public void registerAll(String registry) {
        if (!this.registry.equals(registry) || registered) return;
        registered = true;
        pending.forEach(Runnable::run);
    }

    public static final class Blocks extends DeferredRegister<Block> {
        Blocks(String namespace) { super("block", namespace); }

        public <B extends Block> DeferredBlock<B> registerBlock(String name,
                Function<BlockBehaviour.Properties, ? extends B> factory, UnaryOperator<BlockBehaviour.Properties> properties) {
            var holder = new DeferredBlock<B>(namespace + ":" + name);
            add(holder, () -> factory.apply(properties.apply(new BlockBehaviour.Properties(namespace + ":" + name))));
            return holder;
        }
    }

    public static final class Items extends DeferredRegister<Object> {
        Items(String namespace) { super("item", namespace); }

        public DeferredItem<Object> registerSimpleBlockItem(String name, Supplier<? extends Block> block) {
            var holder = new DeferredItem<Object>(namespace + ":" + name);
            add(holder, () -> new Object[] {"block_item", block.get()});
            return holder;
        }
    }
}
