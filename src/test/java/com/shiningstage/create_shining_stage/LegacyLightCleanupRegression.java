package com.shiningstage.create_shining_stage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.shiningstage.create_shining_stage.LegacyLightCleanupQueue.Cell;
import com.shiningstage.create_shining_stage.LegacyLightCleanupQueue.Section;

/** Exercises the production migration scheduler without initializing Minecraft. */
public final class LegacyLightCleanupRegression {
    public static void main(String[] args) {
        delayedLoadAndRestart();
        emptySourceAndFootprint();
        preserveUnrecordedLightsAndRetry();
        fillEmissionRepairNeverRemovesBlocks();
        System.out.println("Legacy light migration regression passed (unloaded owners, FULL retry, restart, footprint, budget, ownership).");
    }

    private static void delayedLoadAndRestart() {
        Cell source = new Cell(15, 70, -1);
        FakeWorld world = new FakeWorld();
        world.blocks.put(source, "legacy");
        world.loaded.add(LegacyLightCleanupQueue.chunkKey(1, -1));
        LegacyLightCleanupQueue queue = new LegacyLightCleanupQueue();
        queue.add(source, world);
        queue.process(world, 4);
        require(queue.removalPositions().contains(source), "unloaded source record lost");
        require(!world.removed.contains(source), "unloaded chunk was read");
        require(!world.checked.isEmpty(), "loaded neighbour cache repair waits unnecessarily for source owner");

        queue = restart(queue);
        queue.chunkLoaded(source.chunk()); // event fired before FULL is available
        queue.process(world, 4);
        require(queue.removalPositions().contains(source), "pre-FULL load lost record");
        world.loaded.add(source.chunk());
        queue.process(world, 4);
        require(world.removed.contains(source), "FULL retry failed to remove recorded legacy light");
        require(!queue.removalPositions().contains(source), "completed deletion remained pending");

        queue = restart(queue); // source is already gone; unfinished adjacent jobs must survive
        for (Section section : queue.relightJobs().keySet()) {
            world.loaded.add(section.chunk());
            queue.chunkLoaded(section.chunk());
        }
        drain(queue, world);
        require(queue.isEmpty(), "migration did not complete after all relevant chunks loaded");
    }

    private static void emptySourceAndFootprint() {
        for (Cell source : new Cell[] {new Cell(15, 64, -1), new Cell(-17, -32, -16)}) {
            FakeWorld world = new FakeWorld(); // source is AIR, but neighbouring arrays may still glow
            LegacyLightCleanupQueue queue = new LegacyLightCleanupQueue();
            queue.restoreRemoval(source); // old SavedData has only Positions, no relight job tags
            for (Section section : queue.relightJobs().keySet()) world.loaded.add(section.chunk());
            queue.chunkLoaded(source.chunk());
            drain(queue, world);
            Set<Cell> expected = new HashSet<>();
            for (int dx = -15; dx <= 15; dx++) for (int dy = -15; dy <= 15; dy++) for (int dz = -15; dz <= 15; dz++) {
                if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 15) {
                    expected.add(new Cell(source.x() + dx, source.y() + dy, source.z() + dz));
                }
            }
            require(world.checked.equals(expected), "cached-light repair missed or exceeded the old source footprint");
            require(world.removed.isEmpty(), "an air source deleted another block");
        }
    }

    private static void preserveUnrecordedLightsAndRetry() {
        Cell legacy = new Cell(0, 64, 0);
        Cell playerLight = new Cell(1, 64, 0);
        Cell replaced = new Cell(2, 64, 0);
        FakeWorld world = new FakeWorld();
        world.blocks.put(legacy, "legacy");
        world.blocks.put(playerLight, "legacy"); // identical state, but absent from ownership records
        world.blocks.put(replaced, "waterlogged-or-other-level");
        world.failedRemoval.add(legacy);
        LegacyLightCleanupQueue queue = new LegacyLightCleanupQueue();
        queue.add(legacy, world);
        queue.add(replaced, world);
        for (Section section : queue.relightJobs().keySet()) world.loaded.add(section.chunk());
        queue.chunkLoaded(legacy.chunk());
        queue.process(world, 4);
        require(queue.removalPositions().contains(legacy), "failed world write discarded ownership");
        world.failedRemoval.clear();
        drain(queue, world);
        require(world.removed.equals(Set.of(legacy)), "unrecorded/replaced light was deleted");
        require(world.blocks.containsKey(playerLight) && world.blocks.containsKey(replaced), "other lights were changed");
    }

    private static LegacyLightCleanupQueue restart(LegacyLightCleanupQueue old) {
        LegacyLightCleanupQueue copy = new LegacyLightCleanupQueue();
        old.removalPositions().forEach(copy::restoreRemoval);
        old.relightJobs().forEach((section, origins) -> origins.forEach(origin -> copy.restoreRelight(section, origin)));
        return copy;
    }

    private static void fillEmissionRepairNeverRemovesBlocks() {
        FakeWorld world = new FakeWorld();
        Cell source = new Cell(0, 64, 0);
        world.blocks.put(source, "fill-fixture");
        LegacyLightCleanupQueue queue = new LegacyLightCleanupQueue();
        queue.addRelight(source, world);
        require(queue.removalPositions().isEmpty(), "fill self-light repair must not create deletion records");
        for (Section section : queue.relightJobs().keySet()) world.loaded.add(section.chunk());
        drain(restart(queue), world);
        require(world.removed.isEmpty() && world.checked.contains(source), "fill repair deleted its source or skipped recheck");
    }

    private static void drain(LegacyLightCleanupQueue queue, FakeWorld world) {
        for (int i = 0; i < 100 && !queue.isEmpty(); i++) {
            world.sectionsThisPass.clear();
            queue.process(world, 4);
            require(world.sectionsThisPass.size() <= 4, "section budget exceeded");
        }
        require(queue.isEmpty(), "loaded migration stalled");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeWorld implements LegacyLightCleanupQueue.Access {
        final Set<Long> loaded = new HashSet<>();
        final Map<Cell, String> blocks = new HashMap<>();
        final Set<Cell> failedRemoval = new HashSet<>();
        final Set<Cell> removed = new HashSet<>();
        final Set<Cell> checked = new HashSet<>();
        final Set<Section> sectionsThisPass = new HashSet<>();
        @Override public boolean loaded(long chunk) { return loaded.contains(chunk); }
        @Override public boolean removeRecorded(Cell cell) {
            require(loaded(cell.chunk()), "attempted to read an unloaded source chunk");
            if (failedRemoval.contains(cell)) return false;
            if ("legacy".equals(blocks.get(cell))) {
                blocks.remove(cell);
                removed.add(cell);
            }
            return true;
        }
        @Override public void recheck(Cell cell) {
            require(loaded(cell.chunk()), "attempted to repair an unloaded neighbour chunk");
            checked.add(cell);
            sectionsThisPass.add(new Section(cell.x() >> 4, cell.y() >> 4, cell.z() >> 4));
        }
    }
}
