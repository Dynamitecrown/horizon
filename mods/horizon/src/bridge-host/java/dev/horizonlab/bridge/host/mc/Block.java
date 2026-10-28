package dev.horizonlab.bridge.host.mc;

public class Block extends BlockBehaviour {
    public Block(BlockBehaviour.Properties properties) { super(properties); }

    public String id() { return properties.id; }
}
