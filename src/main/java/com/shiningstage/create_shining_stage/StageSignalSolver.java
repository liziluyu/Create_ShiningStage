package com.shiningstage.create_shining_stage;

import java.util.Arrays;
import java.util.PriorityQueue;

/** Pure max-signal propagation for the stage network graph. */
final class StageSignalSolver {
    private StageSignalSolver() {}

    /**
     * Propagates source strengths through directed edges whose costs are zero or one.
     * Each edge row in {@code outgoingEdges} has a matching row in {@code edgeCosts}.
     */
    static int[] solve(int nodeCount, int[] sourceStrengths, int[][] outgoingEdges, int[][] edgeCosts) {
        if (sourceStrengths.length != nodeCount || outgoingEdges.length != nodeCount || edgeCosts.length != nodeCount) {
            throw new IllegalArgumentException("Graph arrays must have nodeCount rows");
        }
        int[] strengths = Arrays.copyOf(sourceStrengths, nodeCount);
        PriorityQueue<Entry> pending = new PriorityQueue<>((a, b) -> Integer.compare(b.strength, a.strength));
        for (int node = 0; node < nodeCount; node++) {
            if (strengths[node] < 0 || strengths[node] > 15 || outgoingEdges[node].length != edgeCosts[node].length) {
                throw new IllegalArgumentException("Invalid source strength or edge row");
            }
            if (strengths[node] > 0) pending.add(new Entry(node, strengths[node]));
        }
        while (!pending.isEmpty()) {
            Entry current = pending.remove();
            if (strengths[current.node] != current.strength) continue;
            int[] edges = outgoingEdges[current.node];
            int[] costs = edgeCosts[current.node];
            for (int i = 0; i < edges.length; i++) {
                int target = edges[i];
                int candidate = current.strength - costs[i];
                if (target < 0 || target >= nodeCount || costs[i] < 0 || costs[i] > 1) {
                    throw new IllegalArgumentException("Invalid graph edge");
                }
                if (candidate > strengths[target]) {
                    strengths[target] = candidate;
                    pending.add(new Entry(target, candidate));
                }
            }
        }
        return strengths;
    }

    private record Entry(int node, int strength) {}
}
