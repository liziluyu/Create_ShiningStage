package com.shiningstage.create_shining_stage;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Resolves the strongest external input shared by a connected tripod/pole network. */
final class StageRedstoneNetwork {
    private static final ThreadLocal<Boolean> RESOLVING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> NOTIFYING = ThreadLocal.withInitial(() -> false);
    private StageRedstoneNetwork() {}

    static int getSignal(BlockGetter level, BlockPos start) {
        if (RESOLVING.get()) return 0;
        RESOLVING.set(true);
        try {
            return resolveSignal(level, start);
        } finally {
            RESOLVING.set(false);
        }
    }

    private static int resolveSignal(BlockGetter level, BlockPos start) {
        Set<BlockPos> network = collectNetwork(level, start);

        int strongest = 0;
        for (BlockPos member : network) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = member.relative(direction);
                if (!network.contains(neighbor)) {
                    int signal = level instanceof SignalGetter signalGetter
                        ? signalGetter.getSignal(neighbor, direction)
                        : level.getBlockState(neighbor).getSignal(level, neighbor, direction);
                    strongest = Math.max(strongest, signal);
                    if (strongest == 15) return strongest;
                }
            }
        }
        return strongest;
    }

    static void notifyNetwork(Level level, BlockPos start, Block source) {
        if (level.isClientSide || NOTIFYING.get()) return;
        NOTIFYING.set(true);
        try {
            for (BlockPos member : collectNetwork(level, start)) {
                level.updateNeighborsAt(member, source);
            }
        } finally {
            NOTIFYING.set(false);
        }
    }

    private static Set<BlockPos> collectNetwork(BlockGetter level, BlockPos start) {
        Set<BlockPos> network = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        BlockPos origin = start.immutable();
        if (!isNetworkBlock(level.getBlockState(origin))) return network;
        network.add(origin);
        pending.add(origin);
        while (!pending.isEmpty()) {
            BlockPos current = pending.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
                if (isNetworkBlock(level.getBlockState(neighbor))) {
                    BlockPos immutableNeighbor = neighbor.immutable();
                    if (network.add(immutableNeighbor)) pending.addLast(immutableNeighbor);
                }
            }
        }
        return network;
    }

    private static boolean isNetworkBlock(BlockState state) {
        return state.getBlock() instanceof TripodBlock || state.getBlock() instanceof PoleBlock;
    }
}
