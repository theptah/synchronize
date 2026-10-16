package net.ptah.synchronize.server;

import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.util.Identifier;
import net.ptah.synchronize.compat.ServerCompat;

public final class VanillaRegistryFilter {

    private static volatile DynamicRegistryManager.Immutable cachedSnapshot;

    public static synchronized void reset() { cachedSnapshot = null; }

    private VanillaRegistryFilter() {
    }

    public static DynamicRegistryManager.Immutable getOrBuild(DynamicRegistryManager.Immutable fullSyncedRegistryManager) {
        DynamicRegistryManager.Immutable snapshot = cachedSnapshot;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (VanillaRegistryFilter.class) {
            if (cachedSnapshot == null) {
                Stream<DynamicRegistryManager.Entry<?>> filteredEntries =
                    //? if >=1.19.3 {
                    fullSyncedRegistryManager.streamAllRegistries()
                    //?} else
                    /*fullSyncedRegistryManager.streamManagedRegistries()*/
                        .filter(entry -> "minecraft".equals(entry.key().getValue().getNamespace()))
                        .map(VanillaRegistryFilter::filterEntry);

                 
                //? if >=1.19.3 {
                cachedSnapshot = new DynamicRegistryManager.ImmutableImpl(filteredEntries).toImmutable();
                //?} else {
                /*java.util.Map<RegistryKey<? extends Registry<?>>, Registry<?>> byKey = new java.util.HashMap<>();
                filteredEntries.forEach(entry -> byKey.put(entry.key(), entry.value()));
                cachedSnapshot = new DynamicRegistryManager.ImmutableImpl(byKey).toImmutable();
                *///?}
            }
            return cachedSnapshot;
        }
    }

    private static <T> DynamicRegistryManager.Entry<T> filterEntry(DynamicRegistryManager.Entry<T> entry) {
        Registry<T> source = entry.value();
        RegistryKey<? extends Registry<T>> registryKey = entry.key();

         
        //? if >=1.19.3 {
        SimpleRegistry<T> filtered = new SimpleRegistry<>(registryKey, source.getLifecycle());
        //?} else
        /*SimpleRegistry<T> filtered = new SimpleRegistry<>(registryKey, source.getLifecycle(), null);*/

        for (Map.Entry<RegistryKey<T>, T> e : source.getEntrySet()) {
            Identifier id = e.getKey().getValue();
            if (!"minecraft".equals(id.getNamespace())) {
                continue;
            }
            ServerCompat.addEntry(filtered, source, e.getKey(), e.getValue());
        }

        return new DynamicRegistryManager.Entry<>(registryKey, filtered.freeze());
    }
}