package dev.horizonlab.bridge.host.neo;

import dev.horizonlab.bridge.host.mc.Block;

public class DeferredBlock<T extends Block> extends DeferredHolder<Block, T> {
    DeferredBlock(String id) { super(id); }
}
