package com.shiningstage.create_shining_stage;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.chunk.DataLayer;

/** Combines immutable light snapshots for vanilla and Sodium section readers, including worker threads. */
final class StageLightLayerMerger {
    private final Map<Long, MergedLayer> layers = new ConcurrentHashMap<>();

    @Nullable DataLayer merge(long section, @Nullable DataLayer original, @Nullable DataLayer virtual) {
        if (virtual == null || virtual.isEmpty()) return original;
        if (original == null || original.isEmpty()) return virtual;
        return layers.compute(section, (key, cached) -> {
            if (cached != null && cached.original == original && cached.virtual == virtual) return cached;
            // getData() would mutate homogeneous input layers. Only read them through get().
            DataLayer merged = new DataLayer();
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                merged.set(x, y, z, Math.max(original.get(x, y, z), virtual.get(x, y, z)));
            }
            return new MergedLayer(original, virtual, merged);
        }).merged;
    }

    void invalidate(long section) { layers.remove(section); }

    void retain(Set<Long> footprint) { layers.keySet().retainAll(footprint); }

    void clear() { layers.clear(); }

    private record MergedLayer(DataLayer original, DataLayer virtual, DataLayer merged) {}
}
