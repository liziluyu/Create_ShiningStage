package com.shiningstage.create_shining_stage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import net.minecraft.world.level.chunk.DataLayer;

/** Exercises real section data and concurrent snapshot reads without bootstrapping Minecraft. */
public final class StageLightLayerMergerRegression {
    public static void main(String[] args) throws Exception {
        StageLightLayerMerger merger = new StageLightLayerMerger();
        for (int real = 0; real < 16; real++) for (int virtual = 0; virtual < 16; virtual++) {
            DataLayer original = new DataLayer(real);
            DataLayer overlay = new DataLayer(virtual);
            DataLayer merged = merger.merge(0, original, overlay);
            check(merged.get(0, 0, 0) == Math.max(real, virtual), "homogeneous brightness");
            check(original.isDefinitelyHomogenous() && overlay.isDefinitelyHomogenous(), "inputs mutated");
        }

        DataLayer original = new DataLayer();
        DataLayer overlay = new DataLayer();
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            original.set(x, y, z, x);
            overlay.set(x, y, z, (y + z) & 15);
        }
        DataLayer merged = merger.merge(1, original, overlay);
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            check(merged.get(x, y, z) == Math.max(x, (y + z) & 15), "nibble/axis indexing");
            check(original.get(x, y, z) == x && overlay.get(x, y, z) == ((y + z) & 15), "aliased inputs");
        }
        check(merger.merge(1, original, overlay) == merged, "unchanged snapshots not cached");

        // A server packet replaces the real layer; it must neither erase virtual light nor reuse
        // an older merge. Removing the virtual layer must immediately expose only the server layer.
        DataLayer packet = new DataLayer(7);
        DataLayer replaced = merger.merge(1, packet, overlay);
        check(replaced != merged && replaced.get(0, 0, 0) == 7, "server replacement reused stale data");
        check(replaced.get(0, 15, 0) == 15, "server replacement erased virtual light");
        check(merger.merge(1, packet, null) == packet, "removed virtual source retained stale light");
        check(merger.merge(1, null, null) == null, "absent section allocated light");
        check(merged.get(0, 0, 0) == 0, "old worker snapshot changed after replacement");

        try (var workers = Executors.newFixedThreadPool(4)) {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) jobs.add(() -> {
                for (int i = 0; i < 100; i++) {
                    DataLayer result = merger.merge(2, packet, overlay);
                    check(result.get(0, 15, 0) == 15 && result.get(0, 0, 0) == 7, "partial publication");
                }
                return null;
            });
            for (var result : workers.invokeAll(jobs)) result.get();
        }
        System.out.println("Stage light layer regression passed (256 brightness pairs, 4096 cells, packet replacement, concurrent reads).");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
