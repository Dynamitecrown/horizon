package dev.horizonlab.bridge.host.mc;

public record BlockPos(int x, int y, int z) {
    public String toShortString() { return x + ", " + y + ", " + z; }
}
