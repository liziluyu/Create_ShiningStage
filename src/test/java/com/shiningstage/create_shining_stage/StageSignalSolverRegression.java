package com.shiningstage.create_shining_stage;

import java.util.ArrayList;
import java.util.List;

/** Dependency-free regression cases for the production stage redstone graph solver. */
public final class StageSignalSolverRegression {
    private StageSignalSolverRegression() {}

    public static void main(String[] args) {
        stageOutputCanReachAnotherStageThroughDust();
        longStageChainDoesNotAttenuateAndEdgesAreDirected();
        dustAttenuatesWireToWireAndKeepsStrongestInput();
        independentInputsStayIndependentAndStrengthCapsAtFifteen();
        longDustLineAttenuatesToZero();
        mergeAndSplitRecomputeFromTheirCurrentGraph();
        removingTheLastExternalSourceDoesNotLeaveALatchedCycle();
        System.out.println("StageSignalSolver regression cases passed");
    }

    private static void stageOutputCanReachAnotherStageThroughDust() {
        // Nodes: external source, stage A, dust beside A, dust beside B, stage B.
        int[] signal = solve(5, new int[] {15, 0, 0, 0, 0},
            edge(0, 1, 0), edge(1, 2, 0), edge(2, 3, 1), edge(3, 4, 0));
        equal(15, signal[1], "stage A reads its source");
        equal(14, signal[3], "wire line loses one level between dust wires");
        equal(14, signal[4], "stage B receives stage A through dust");
    }

    private static void longStageChainDoesNotAttenuateAndEdgesAreDirected() {
        int stageCount = 64;
        Edge[] chain = new Edge[stageCount - 1];
        for (int stage = 0; stage < chain.length; stage++) chain[stage] = edge(stage, stage + 1, 0);

        int[] fromLeft = solve(stageCount, withSourceAt(stageCount, 0, 11), chain);
        equal(11, fromLeft[stageCount - 1], "long connected stage chain has no signal loss");

        int[] fromRight = solve(stageCount, withSourceAt(stageCount, stageCount - 1, 8), chain);
        equal(0, fromRight[0], "one-way edges do not power nodes against their direction");
    }

    private static void dustAttenuatesWireToWireAndKeepsStrongestInput() {
        // Two source paths meet at the same wire chain. The stronger one wins, then each
        // wire-to-wire edge attenuates by one; the source-to-wire and wire-to-stage edges do not.
        int[] signal = solve(5, new int[] {12, 15, 0, 0, 0},
            edge(0, 2, 0), edge(1, 2, 0), edge(2, 3, 1), edge(3, 4, 0));
        equal(15, signal[2], "wire takes strongest adjacent source");
        equal(14, signal[3], "dust attenuates by one");
        equal(14, signal[4], "connected stage receives dust output");
    }

    private static void independentInputsStayIndependentAndStrengthCapsAtFifteen() {
        int[] signal = solve(5, new int[] {15, 9, 0, 0, 0},
            edge(0, 3, 0), edge(1, 4, 0), edge(2, 4, 0));
        equal(15, signal[3], "signal strength has a fifteen-level maximum");
        equal(9, signal[4], "isolated signal stays independent from the stronger network");
    }

    private static void longDustLineAttenuatesToZero() {
        int wireCount = 17;
        int nodeCount = wireCount + 2;
        Edge[] edges = new Edge[wireCount + 1];
        edges[0] = edge(0, 1, 0); // external source -> first wire
        for (int wire = 1; wire < wireCount; wire++) {
            edges[wire] = edge(wire, wire + 1, 1);
        }
        edges[wireCount] = edge(wireCount, nodeCount - 1, 0); // last wire -> stage
        int[] source = new int[nodeCount];
        source[0] = 15;
        int[] signal = solve(nodeCount, source, edges);
        equal(0, signal[nodeCount - 1], "dust line longer than the available strength reaches zero");
    }

    private static void mergeAndSplitRecomputeFromTheirCurrentGraph() {
        // A merge joins two stage nodes into one component, so both see the strongest input.
        int[] merged = solve(4, new int[] {15, 0, 7, 0},
            edge(0, 1, 0), edge(1, 3, 0), edge(2, 3, 0));
        equal(15, merged[1], "left side of merged component");
        equal(15, merged[3], "right side of merged component");

        // Rebuild with the inter-stage edge removed: the two components now retain only their
        // respective external inputs. This models a split after block removal.
        int[] split = solve(4, new int[] {15, 0, 7, 0},
            edge(0, 1, 0), edge(2, 3, 0));
        equal(15, split[1], "left component after split");
        equal(7, split[3], "right component after split");
    }

    private static void removingTheLastExternalSourceDoesNotLeaveALatchedCycle() {
        // Stage and dust can feed each other, but removing the only external seed makes the fixed
        // point zero even though the topology still contains the prior output path and a cycle.
        Edge[] graph = {
            edge(0, 1, 0), edge(1, 2, 0), edge(2, 1, 0)};
        int[] powered = solve(3, new int[] {15, 0, 0}, graph);
        equal(15, powered[1], "external source powers cyclic network");
        int[] unpowered = solve(3, new int[] {0, 0, 0}, graph);
        equal(0, unpowered[1], "source removal clears cyclic network output");
        equal(0, unpowered[2], "source removal clears stale dust POWER");
    }

    private static int[] solve(int nodeCount, int[] sources, Edge... edges) {
        List<List<Edge>> bySource = new ArrayList<>(nodeCount);
        for (int node = 0; node < nodeCount; node++) bySource.add(new ArrayList<>());
        for (Edge edge : edges) bySource.get(edge.from()).add(edge);

        int[][] outgoing = new int[nodeCount][];
        int[][] costs = new int[nodeCount][];
        for (int node = 0; node < nodeCount; node++) {
            List<Edge> nodeEdges = bySource.get(node);
            outgoing[node] = new int[nodeEdges.size()];
            costs[node] = new int[nodeEdges.size()];
            for (int index = 0; index < nodeEdges.size(); index++) {
                Edge edge = nodeEdges.get(index);
                outgoing[node][index] = edge.to();
                costs[node][index] = edge.cost();
            }
        }
        return StageSignalSolver.solve(nodeCount, sources, outgoing, costs);
    }

    private static Edge edge(int from, int to, int cost) {
        return new Edge(from, to, cost);
    }

    private static int[] withSourceAt(int nodeCount, int node, int strength) {
        int[] sources = new int[nodeCount];
        sources[node] = strength;
        return sources;
    }

    private static void equal(int expected, int actual, String description) {
        if (expected != actual) {
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
        }
    }

    private record Edge(int from, int to, int cost) {}
}
