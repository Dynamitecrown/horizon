package dev.horizonlab.bridge.host;

import dev.horizonlab.bridge.host.neo.DeferredRegister;
import dev.horizonlab.bridge.host.neo.IEventBus;
import dev.horizonlab.bridge.host.neo.Mod;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Loads a remapped mod jar as NeoForge would for the parts the fixtures use: finds the {@code @Mod}
 * class for the mod id, constructs it with the mod event bus, then runs the deferred registrations
 * in registry order (blocks, items, block entity types).
 */
public final class BridgeLoader {
    private BridgeLoader() {}

    public static Object load(Path jar, String modId, ClassLoader loader) throws ReflectiveOperationException, IOException {
        Class<?> modClass = null;
        try (var zip = new ZipFile(jar.toFile())) {
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class") || name.contains("$")) continue;
                Class<?> type = Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false, loader);
                Mod mod = type.getAnnotation(Mod.class);
                if (mod != null && mod.value().equals(modId)) {
                    if (modClass != null) throw new IllegalStateException("Two @Mod classes for " + modId);
                    modClass = type;
                }
            }
        }
        if (modClass == null) throw new IllegalStateException("No @Mod class for " + modId);
        var bus = new IEventBus.ModBus();
        Object instance = modClass.getConstructor(IEventBus.class).newInstance(bus);
        for (String registry : List.of("block", "item", "block_entity_type")) {
            for (DeferredRegister<?> register : new ArrayList<>(bus.registers)) {
                register.registerAll(registry);
            }
        }
        return instance;
    }

    /** Loads each mod and prints what it registered: {@code BridgeLoader <jar> <mod id> [<jar> <mod id> ...]}. */
    public static void main(String[] args) throws Exception {
        for (int i = 0; i + 1 < args.length; i += 2) {
            Host.REGISTRATIONS.clear();
            load(Path.of(args[i]), args[i + 1], BridgeLoader.class.getClassLoader());
            System.out.println(args[i + 1] + " " + Host.REGISTRATIONS);
        }
    }
}
