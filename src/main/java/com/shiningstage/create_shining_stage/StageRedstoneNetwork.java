package com.shiningstage.create_shining_stage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.TickEvent;

/** Resolves loaded stage/dust networks on updates; block signal reads are a state lookup. */
final class StageRedstoneNetwork {
    static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);

    private static final Map<Level, LevelCache> LEVELS = new WeakHashMap<>();

    private StageRedstoneNetwork() {}

    /** The synced blockstate is the O(1) signal cache used by both server and client. */
    static int getSignal(BlockState state) {
        return state.getValue(POWER);
    }

    static void notifyNetwork(Level level, BlockPos start, Block source) {
        if (level.isClientSide) return;
        cache(level).pendingStages.add(start.immutable());
    }

    static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        LevelCache cache = cache(level);
        Set<Long> graphs = cache.dependencyGraphs.get(event.getPos().immutable());
        if (graphs != null) queueGraphs(cache, graphs);
    }

    static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        cache(level).pendingLoads.add(chunkKey(event.getChunk().getPos().x, event.getChunk().getPos().z));
    }

    static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        LevelCache cache = cache(level);
        long key = chunkKey(event.getChunk().getPos().x, event.getChunk().getPos().z);
        cache.unloadedChunks.add(key);
        cache.pendingLoads.remove(key);
        Set<Long> graphs = cache.chunkGraphs.get(key);
        if (graphs != null) cache.dirtyGraphs.addAll(graphs);
    }

    static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Level level = event.level;
        if (level.isClientSide) return;
        LevelCache cache = cache(level);
        if (!cache.pendingLoads.isEmpty()) {
            var loads = cache.pendingLoads.iterator();
            while (loads.hasNext()) {
                long key = loads.next();
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                // ChunkEvent.Load may run before FULL promotion. Retry next tick rather than
                // requesting the chunk or losing the only initialization notification.
                if (chunk == null) continue;
                cache.unloadedChunks.remove(key);
                Set<Long> graphs = cache.chunkGraphs.get(key);
                if (graphs != null) queueGraphs(cache, graphs);
                collectLoadedChunkStages(level, cache, chunk);
                loads.remove();
            }
        }

        Set<BlockPos> starts = new HashSet<>(cache.pendingStages);
        cache.pendingStages.clear();
        if (!cache.dirtyGraphs.isEmpty()) {
            for (long graphId : cache.dirtyGraphs) {
                GraphSnapshot graph = cache.graphs.get(graphId);
                if (graph != null) starts.addAll(graph.stagePositions);
                // Also forget components whose last stage block was removed; they have no live
                // start from which recompute could otherwise retire the snapshot.
                removeGraph(cache, graphId);
            }
            cache.dirtyGraphs.clear();
        }
        Set<BlockPos> processed = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>(starts);
        while (!pending.isEmpty()) {
            BlockPos stage = pending.removeFirst();
            if (processed.contains(stage) || !isLoaded(level, stage) || !isStageState(level.getBlockState(stage))) continue;
            Set<BlockPos> componentStages = recompute(level, stage, pending);
            processed.addAll(componentStages);
        }
        cache.unloadedChunks.removeIf(key -> {
            int chunkX = (int) (key >> 32);
            int chunkZ = key.intValue();
            return !level.getChunkSource().hasChunk(chunkX, chunkZ);
        });
    }

    private static void collectLoadedChunkStages(Level level, LevelCache cache, LevelChunk chunk) {
        int minY = level.getMinBuildHeight();
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || !section.maybeHas(StageRedstoneNetwork::isStageState)) continue;
            int y0 = level.getMinBuildHeight() + (sectionIndex << 4);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int y = Math.max(minY, y0); y < y0 + 16 && y < level.getMaxBuildHeight(); y++) {
                for (int x = chunk.getPos().getMinBlockX(); x < chunk.getPos().getMinBlockX() + 16; x++) {
                    for (int z = chunk.getPos().getMinBlockZ(); z < chunk.getPos().getMinBlockZ() + 16; z++) {
                        cursor.set(x, y, z);
                        BlockState state = chunk.getBlockState(cursor);
                        if (isStageState(state)) {
                            cache.pendingStages.add(cursor.immutable());
                        }
                    }
                }
            }
        }
    }

    private static Set<BlockPos> recompute(Level level, BlockPos start, ArrayDeque<BlockPos> pending) {
        if (!isLoaded(level, start)) return Set.of();
        LevelCache cache = cache(level);
        if (!isStageState(level.getBlockState(start))) return Set.of();

        Graph graph = collectGraph(level, start);
        if (graph.stagePositions.isEmpty()) return Set.of();

        Set<Long> replaced = new HashSet<>();
        for (BlockPos pos : graph.positions) {
            Long existing = cache.nodeGraphs.get(pos);
            if (existing != null) replaced.add(existing);
        }
        Long oldAtStart = cache.nodeGraphs.get(start);
        if (oldAtStart != null) replaced.add(oldAtStart);
        for (long id : replaced) {
            GraphSnapshot previous = cache.graphs.get(id);
            if (previous != null) {
                // A topology edit can split one snapshot into several components. Rebuild every
                // surviving side, including sides not reachable from this particular start.
                for (BlockPos stage : previous.stagePositions) {
                    if (!graph.stagePositions.contains(stage)) pending.addLast(stage);
                }
            }
            removeGraph(cache, id);
        }

        int[][] outgoing = new int[graph.positions.size()][];
        int[][] costs = new int[graph.positions.size()][];
        int[] seeds = new int[graph.positions.size()];
        for (int i = 0; i < graph.positions.size(); i++) {
            List<Edge> edges = graph.edges.get(i);
            outgoing[i] = new int[edges.size()];
            costs[i] = new int[edges.size()];
            for (int j = 0; j < edges.size(); j++) {
                outgoing[i][j] = edges.get(j).target;
                costs[i][j] = edges.get(j).cost;
            }
            seeds[i] = externalSignal(level, graph, i);
        }
        int[] powers = StageSignalSolver.solve(graph.positions.size(), seeds, outgoing, costs);

        long id = ++cache.nextGraphId;
        GraphSnapshot snapshot = new GraphSnapshot(id, graph.positions, graph.stagePositions,
            graph.dependencies, graph.touchedChunks);
        addGraph(cache, snapshot);
        List<BlockPos> changed = new ArrayList<>();
        Map<BlockPos, BlockState> updates = new HashMap<>();
        for (BlockPos pos : graph.stagePositions) {
            if (!isLoaded(level, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!isStageState(state)) continue;
            int power = powers[graph.indices.get(pos)];
            if (state.getValue(POWER) != power) {
                level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                changed.add(pos);
                updates.put(pos, state);
            }
        }
        for (BlockPos pos : changed) notifyLoadedNeighbors(level, pos, updates.get(pos).getBlock());
        return graph.stagePositions;
    }

    private static void notifyLoadedNeighbors(Level level, BlockPos pos, Block source) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!isLoaded(level, neighbor)) continue;
            level.neighborChanged(neighbor, source, pos);
        }
    }

    private static void queueGraphs(LevelCache cache, Set<Long> graphIds) {
        // A single dust update may broadcast many notifications. Coalesce by snapshot id instead
        // of copying all N stage positions for each notification.
        cache.dirtyGraphs.addAll(graphIds);
    }

    private static Graph collectGraph(Level level, BlockPos start) {
        Graph graph = new Graph();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        addNode(graph, start.immutable(), true);
        enqueue(graph, pending, start);
        while (!pending.isEmpty()) {
            BlockPos current = pending.removeFirst();
            int currentIndex = graph.indices.get(current);
            BlockState currentState = readState(level, graph, current);
            boolean stage = isStageState(currentState);

            if (stage) {
                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = current.relative(direction);
                    BlockState neighborState = readState(level, graph, neighbor);
                    if (isStageState(neighborState)) {
                        int next = addNode(graph, neighbor.immutable(), true);
                        addEdge(graph, currentIndex, next, 0);
                        addEdge(graph, next, currentIndex, 0);
                        enqueue(graph, pending, neighbor);
                    } else if (isWire(neighborState)) {
                        int next = addNode(graph, neighbor.immutable(), false);
                        addEdge(graph, currentIndex, next, 0);
                        if (dustEmitsTo(neighborState, direction)) addEdge(graph, next, currentIndex, 0);
                        enqueue(graph, pending, neighbor);
                    } else {
                        addConductorDustToStageEdges(level, graph, pending, currentIndex, neighbor, direction);
                    }
                }
            } else {
                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = current.relative(direction);
                    BlockState neighborState = readState(level, graph, neighbor);
                    if (isStageState(neighborState)) {
                        int stageIndex = addNode(graph, neighbor.immutable(), true);
                        addEdge(graph, stageIndex, currentIndex, 0);
                        if (dustEmitsTo(currentState, direction.getOpposite())) {
                            addEdge(graph, currentIndex, stageIndex, 0);
                        }
                        enqueue(graph, pending, neighbor);
                    } else if (!isWire(neighborState) && dustEmitsTo(currentState, direction.getOpposite())) {
                        // A dust wire can strongly power a solid neighbor, which in turn supplies
                        // a stage beside it. Discover this path from either end so cache ownership
                        // does not depend on which stage was recomputed first.
                        for (Direction conductorToStage : Direction.values()) {
                            BlockPos receiver = neighbor.relative(conductorToStage);
                            if (!isStageState(readState(level, graph, receiver))) continue;
                            if (!neighborState.shouldCheckWeakPower(level, neighbor, conductorToStage.getOpposite())) continue;
                            int receiverIndex = addNode(graph, receiver.immutable(), true);
                            addEdge(graph, currentIndex, receiverIndex, 0);
                            enqueue(graph, pending, receiver);
                        }
                    }
                }
                addNearbyWireCandidates(level, graph, pending, current, currentIndex);
            }
            graph.expanded.add(current);
        }
        return graph;
    }

    private static void addNearbyWireCandidates(Level level, Graph graph, ArrayDeque<BlockPos> pending,
                                                BlockPos current, int currentIndex) {
        Set<BlockPos> inputs = wireInputs(level, graph, current);
        for (Direction horizontal : Direction.Plane.HORIZONTAL) for (int dy = -1; dy <= 1; dy++) {
            BlockPos possible = current.relative(horizontal).offset(0, dy, 0).immutable();
            if (!isWire(readState(level, graph, possible))) continue;
            boolean possibleFeedsCurrent = inputs.contains(possible);
            boolean currentFeedsPossible = wireInputs(level, graph, possible).contains(current);
            if (!possibleFeedsCurrent && !currentFeedsPossible) continue;
            int next = addNode(graph, possible, false);
            if (possibleFeedsCurrent) addEdge(graph, next, currentIndex, 1);
            if (currentFeedsPossible) addEdge(graph, currentIndex, next, 1);
            enqueue(graph, pending, possible);
        }
    }

    private static Set<BlockPos> wireInputs(Level level, Graph graph, BlockPos target) {
        Set<BlockPos> inputs = new HashSet<>();
        BlockPos aboveTarget = target.above();
        boolean openAbove = !readState(level, graph, aboveTarget).isRedstoneConductor(level, aboveTarget);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = target.relative(direction);
            inputs.add(side.immutable());
            BlockState sideState = readState(level, graph, side);
            if (sideState.isRedstoneConductor(level, side)) {
                BlockPos above = side.above();
                if (openAbove) inputs.add(above.immutable());
            } else {
                BlockPos below = side.below();
                inputs.add(below.immutable());
            }
        }
        return inputs;
    }

    private static boolean dustEmitsTo(BlockState wireState, Direction targetToWire) {
        if (targetToWire == Direction.DOWN) return false;
        if (targetToWire == Direction.UP) return true;
        Direction wireToTarget = targetToWire.getOpposite();
        return wireState.getValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(wireToTarget)) != RedstoneSide.NONE;
    }

    private static void addConductorDustToStageEdges(Level level, Graph graph, ArrayDeque<BlockPos> pending,
                                                     int stageIndex, BlockPos conductor, Direction stageToConductor) {
        if (!readState(level, graph, conductor).shouldCheckWeakPower(level, conductor, stageToConductor)) return;
        for (Direction conductorToWire : Direction.values()) {
            BlockPos wire = conductor.relative(conductorToWire).immutable();
            BlockState wireState = readState(level, graph, wire);
            if (isWire(wireState) && dustEmitsTo(wireState, conductorToWire)) {
                int wireIndex = addNode(graph, wire, false);
                addEdge(graph, wireIndex, stageIndex, 0);
                enqueue(graph, pending, wire);
            }
        }
    }

    private static int externalSignal(Level level, Graph graph, int node) {
        BlockPos target = graph.positions.get(node);
        int strongest = 0;
        for (Direction direction : Direction.values()) {
            BlockPos source = target.relative(direction);
            BlockState sourceState = readState(level, graph, source);
            if (graph.indices.containsKey(source)) continue;
            if (isStageState(sourceState) || isWire(sourceState)) continue;
            int signal = sourceState.getSignal(level, source, direction);
            if (sourceState.shouldCheckWeakPower(level, source, direction)) {
                signal = Math.max(signal, externalDirectSignal(level, graph, source));
            }
            strongest = Math.max(strongest, signal);
            if (strongest == 15) return 15;
        }
        return strongest;
    }

    private static int externalDirectSignal(Level level, Graph graph, BlockPos target) {
        int strongest = 0;
        for (Direction direction : Direction.values()) {
            BlockPos source = target.relative(direction);
            BlockState state = readState(level, graph, source);
            if (graph.indices.containsKey(source)) continue;
            if (isStageState(state) || isWire(state)) continue;
            strongest = Math.max(strongest, state.getDirectSignal(level, source, direction));
            if (strongest == 15) return 15;
        }
        return strongest;
    }

    private static BlockState readState(Level level, Graph graph, BlockPos pos) {
        // Track failed reads too: a newly loaded boundary/source chunk must invalidate the graph
        // even when it contains no stage blocks of its own.
        graph.dependencies.add(pos.immutable());
        graph.touchedChunks.add(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        return isLoaded(level, pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    private static boolean isLoaded(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        return !cache(level).unloadedChunks.contains(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
    }

    private static boolean isStageState(BlockState state) {
        return state.getBlock() instanceof TripodBlock || state.getBlock() instanceof PoleBlock;
    }

    private static boolean isWire(BlockState state) {
        return state.getBlock() instanceof RedStoneWireBlock;
    }

    private static int addNode(Graph graph, BlockPos pos, boolean stage) {
        Integer found = graph.indices.get(pos);
        if (found != null) return found;
        int index = graph.positions.size();
        graph.indices.put(pos, index);
        graph.positions.add(pos);
        graph.edges.add(new ArrayList<>());
        if (stage) graph.stagePositions.add(pos);
        graph.touchedChunks.add(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
        return index;
    }

    private static void enqueue(Graph graph, ArrayDeque<BlockPos> pending, BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (!graph.expanded.contains(immutable) && graph.queued.add(immutable)) pending.addLast(immutable);
    }

    private static void addEdge(Graph graph, int from, int to, int cost) {
        if (from < 0 || to < 0 || from == to) return;
        List<Edge> edges = graph.edges.get(from);
        for (Edge edge : edges) if (edge.target == to && edge.cost <= cost) return;
        edges.add(new Edge(to, cost));
    }

    private static void addGraph(LevelCache cache, GraphSnapshot graph) {
        cache.graphs.put(graph.id, graph);
        for (BlockPos pos : graph.positions) cache.nodeGraphs.put(pos, graph.id);
        for (BlockPos dependency : graph.dependencies) cache.dependencyGraphs.computeIfAbsent(dependency, ignored -> new HashSet<>()).add(graph.id);
        for (long chunk : graph.touchedChunks) cache.chunkGraphs.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(graph.id);
    }

    private static void removeGraph(LevelCache cache, long id) {
        GraphSnapshot graph = cache.graphs.remove(id);
        if (graph == null) return;
        for (BlockPos pos : graph.positions) cache.nodeGraphs.remove(pos, id);
        for (BlockPos dependency : graph.dependencies) removeIndex(cache.dependencyGraphs, dependency, id);
        for (long chunk : graph.touchedChunks) removeIndex(cache.chunkGraphs, chunk, id);
    }

    private static <K> void removeIndex(Map<K, Set<Long>> index, K key, long id) {
        Set<Long> ids = index.get(key);
        if (ids != null && ids.remove(id) && ids.isEmpty()) index.remove(key);
    }

    private static LevelCache cache(Level level) {
        synchronized (LEVELS) {
            return LEVELS.computeIfAbsent(level, ignored -> new LevelCache());
        }
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static final class Graph {
        final Map<BlockPos, Integer> indices = new HashMap<>();
        final List<BlockPos> positions = new ArrayList<>();
        final List<List<Edge>> edges = new ArrayList<>();
        final Set<BlockPos> stagePositions = new HashSet<>();
        final Set<BlockPos> dependencies = new HashSet<>();
        final Set<Long> touchedChunks = new HashSet<>();
        final Set<BlockPos> expanded = new HashSet<>();
        final Set<BlockPos> queued = new HashSet<>();
    }

    private record Edge(int target, int cost) {}

    private record GraphSnapshot(long id, List<BlockPos> positions, Set<BlockPos> stagePositions,
                                 Set<BlockPos> dependencies, Set<Long> touchedChunks) {}

    private static final class LevelCache {
        long nextGraphId;
        final Map<Long, GraphSnapshot> graphs = new HashMap<>();
        final Map<BlockPos, Long> nodeGraphs = new HashMap<>();
        final Map<BlockPos, Set<Long>> dependencyGraphs = new HashMap<>();
        final Map<Long, Set<Long>> chunkGraphs = new HashMap<>();
        final Set<Long> dirtyGraphs = new HashSet<>();
        final Set<Long> pendingLoads = new HashSet<>();
        final Set<Long> unloadedChunks = new HashSet<>();
        final Set<BlockPos> pendingStages = new HashSet<>();
    }
}
