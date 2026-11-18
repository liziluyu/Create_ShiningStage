package com.shiningstage.create_shining_stage;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import org.joml.Vector3d;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/** Loaded only through the guarded entry points in {@link SableCompat}. */
final class SableIntegration {
    /**
     * Sable's teardown does not invoke Block#onRemove. Find the departing structure's spotlights
     * while its plot chunks still exist, and defer their world writes until teardown has unwound.
     * Both removal and unload need cleanup: a returning ship replaces its lights on its first poll.
     */
    private static final SubLevelObserver LIGHT_CLEANUP = new SubLevelObserver() {
        @Override
        public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
            for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
                LevelChunk chunk = holder.getChunk();
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof SpotlightBlockEntity spotlight) {
                        spotlight.removePlacedLightsLater();
                    }
                }
            }
        }
    };

    private static final Set<Level> OBSERVED_LEVELS = Collections.newSetFromMap(new WeakHashMap<>());

    private SableIntegration() {
    }

    static double distanceSquared(Level level, Vec3 first, Vec3 second) {
        return Sable.HELPER.distanceSquaredWithSubLevels(level, first, second);
    }

    static Vec3 toWorldPosition(Level level, BlockPos owner, Vec3 position) {
        SubLevelAccess subLevel = Sable.HELPER.getContaining(level, owner);
        Pose3dc pose = subLevel == null ? null : subLevel.logicalPose();
        if (pose == null) {
            return position;
        }
        Vector3d projected = new Vector3d(position.x, position.y, position.z);
        pose.transformPosition(projected);
        return new Vec3(projected.x, projected.y, projected.z);
    }

    static boolean isInPlotGrid(Level level, BlockPos position) {
        return Sable.HELPER.isInPlotGrid(level, position);
    }

    static void trackExternalLights(Level level, BlockPos owner, Set<BlockPos> lights) {
        if (!isInPlotGrid(level, owner)) {
            return;
        }
        // Check the recorded cells each poll, so assembling an already-lit spotlight also registers.
        for (BlockPos light : lights) {
            if (!isInPlotGrid(level, light)) {
                observeSubLevelRemovals(level);
                return;
            }
        }
    }

    private static void observeSubLevelRemovals(Level level) {
        if (!OBSERVED_LEVELS.add(level)) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            // Retry on a later poll if Sable has not created this level's container yet.
            OBSERVED_LEVELS.remove(level);
            return;
        }
        container.addObserver(LIGHT_CLEANUP);
    }
}
