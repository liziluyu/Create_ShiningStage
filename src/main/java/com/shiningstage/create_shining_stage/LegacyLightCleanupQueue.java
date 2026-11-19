package com.shiningstage.create_shining_stage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Persistent migration work, independent of Minecraft so unload/restart cases can be tested. */
final class LegacyLightCleanupQueue {
    private static final int LIGHT_RADIUS = 15;
    private final Map<Long, Set<Cell>> removals = new HashMap<>();
    private final Map<Long, Map<Integer, Set<Cell>>> relights = new HashMap<>();
    private final Set<Long> ready = new HashSet<>();
    private boolean initialized;

    record Cell(int x, int y, int z) {
        long chunk() { return chunkKey(x >> 4, z >> 4); }
    }
    record Section(int x, int y, int z) {
        long chunk() { return chunkKey(x, z); }
    }
    interface Access {
        boolean loaded(long chunk);
        /** Only a recorded legacy light may be removed; false retains the record for retry. */
        boolean removeRecorded(Cell cell);
        /** Recalculate cached light from actual blocks, preserving every other light source. */
        void recheck(Cell cell);
    }

    boolean add(Cell cell, Access access) {
        boolean changed = removals.computeIfAbsent(cell.chunk(), ignored -> new HashSet<>()).add(cell);
        changed |= addRelight(cell, access);
        if (access.loaded(cell.chunk())) ready.add(cell.chunk());
        return changed;
    }

    boolean addRelight(Cell cell, Access access) {
        boolean changed = scheduleRelight(cell);
        activateFootprint(cell, access);
        initialized = false; // also discover FULL promotions completed after the owner's onLoad
        return changed;
    }

    void restoreRemoval(Cell cell) {
        removals.computeIfAbsent(cell.chunk(), ignored -> new HashSet<>()).add(cell);
        scheduleRelight(cell);
    }

    void restoreRelight(Section section, Cell origin) {
        relights.computeIfAbsent(section.chunk(), ignored -> new HashMap<>())
            .computeIfAbsent(section.y, ignored -> new HashSet<>()).add(origin);
    }

    private boolean scheduleRelight(Cell cell) {
        boolean changed = false;
        for (int x = (cell.x - LIGHT_RADIUS) >> 4; x <= (cell.x + LIGHT_RADIUS) >> 4; x++) {
            for (int z = (cell.z - LIGHT_RADIUS) >> 4; z <= (cell.z + LIGHT_RADIUS) >> 4; z++) {
                for (int y = (cell.y - LIGHT_RADIUS) >> 4; y <= (cell.y + LIGHT_RADIUS) >> 4; y++) {
                    changed |= relights.computeIfAbsent(chunkKey(x, z), ignored -> new HashMap<>())
                        .computeIfAbsent(y, ignored -> new HashSet<>()).add(cell);
                }
            }
        }
        return changed;
    }

    private void activateFootprint(Cell cell, Access access) {
        for (int x = (cell.x - LIGHT_RADIUS) >> 4; x <= (cell.x + LIGHT_RADIUS) >> 4; x++) {
            for (int z = (cell.z - LIGHT_RADIUS) >> 4; z <= (cell.z + LIGHT_RADIUS) >> 4; z++) {
                long chunk = chunkKey(x, z);
                if (access.loaded(chunk)) ready.add(chunk);
            }
        }
    }

    void chunkLoaded(long chunk) {
        if (removals.containsKey(chunk) || relights.containsKey(chunk)) ready.add(chunk);
    }
    void chunkUnloaded(long chunk) { ready.remove(chunk); }

    boolean process(Access access, int sectionBudget) {
        if (!initialized) {
            initialized = true;
            Set<Long> chunks = new HashSet<>(removals.keySet());
            chunks.addAll(relights.keySet());
            for (long chunk : chunks) if (access.loaded(chunk)) ready.add(chunk);
        }
        boolean changed = false;
        int checkedSections = 0;
        int removedSources = 0;
        for (long chunk : Set.copyOf(ready)) {
            // Load can precede FULL promotion; retry until available or an unload arrives.
            if (!access.loaded(chunk)) continue;
            Set<Cell> pending = removals.get(chunk);
            if (pending != null) {
                for (Cell cell : Set.copyOf(pending)) {
                    if (removedSources >= 256) break;
                    removedSources++;
                    if (!access.removeRecorded(cell)) continue;
                    pending.remove(cell);
                    scheduleRelight(cell);
                    activateFootprint(cell, access);
                    changed = true;
                }
                if (pending.isEmpty()) removals.remove(chunk);
            }
            Map<Integer, Set<Cell>> sections = relights.get(chunk);
            if (sections != null) {
                for (int sectionY : Set.copyOf(sections.keySet())) {
                    if (checkedSections >= sectionBudget) break;
                    Set<Cell> origins = sections.remove(sectionY);
                    int minX = (int) chunk << 4;
                    int minY = sectionY << 4;
                    int minZ = (int) (chunk >> 32) << 4;
                    Set<Cell> checks = new HashSet<>();
                    for (Cell origin : origins) {
                        for (int x = Math.max(minX, origin.x - LIGHT_RADIUS); x <= Math.min(minX + 15, origin.x + LIGHT_RADIUS); x++) {
                            for (int z = Math.max(minZ, origin.z - LIGHT_RADIUS); z <= Math.min(minZ + 15, origin.z + LIGHT_RADIUS); z++) {
                                int yRadius = LIGHT_RADIUS - Math.abs(x - origin.x) - Math.abs(z - origin.z);
                                for (int y = Math.max(minY, origin.y - yRadius); y <= Math.min(minY + 15, origin.y + yRadius); y++) {
                                    checks.add(new Cell(x, y, z));
                                }
                            }
                        }
                    }
                    checks.forEach(access::recheck);
                    checkedSections++;
                    changed = true;
                }
                if (sections.isEmpty()) relights.remove(chunk);
            }
            if (!removals.containsKey(chunk) && !relights.containsKey(chunk)) ready.remove(chunk);
        }
        return changed;
    }

    Set<Cell> removalPositions() {
        Set<Cell> cells = new HashSet<>();
        removals.values().forEach(cells::addAll);
        return cells;
    }
    Map<Section, Set<Cell>> relightJobs() {
        Map<Section, Set<Cell>> copy = new HashMap<>();
        relights.forEach((chunk, sections) -> sections.forEach((y, cells) ->
            copy.put(new Section((int) (long) chunk, y, (int) (chunk >> 32)), Set.copyOf(cells))));
        return copy;
    }
    boolean isEmpty() { return removals.isEmpty() && relights.isEmpty(); }
    static long chunkKey(int x, int z) { return (x & 0xffffffffL) | ((long) z << 32); }
}
