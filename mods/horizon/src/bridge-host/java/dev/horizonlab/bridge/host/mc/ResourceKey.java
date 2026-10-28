package dev.horizonlab.bridge.host.mc;

/** A registry key; only the registry's path is used ("block_entity_type"). */
public record ResourceKey<T>(String path) {}
