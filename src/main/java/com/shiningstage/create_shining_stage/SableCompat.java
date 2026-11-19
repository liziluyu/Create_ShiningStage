package com.shiningstage.create_shining_stage;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Entry points with no Sable types in their signatures; safe to load without Sable installed. */
final class SableCompat {
    private static final boolean LOADED = ModList.get().isLoaded("sable");

    private SableCompat() {
    }

    static double distanceSquared(Level level, Vec3 first, Vec3 second) {
        return LOADED ? SableIntegration.distanceSquared(level, first, second) : first.distanceToSqr(second);
    }

    static Vec3 toWorldPosition(Level level, BlockPos owner, Vec3 position) {
        return LOADED ? SableIntegration.toWorldPosition(level, owner, position) : position;
    }

    static boolean isInPlotGrid(Level level, BlockPos position) {
        return LOADED && SableIntegration.isInPlotGrid(level, position);
    }

    static void checkBlockLight(Level level, BlockPos position) {
        if (LOADED) {
            SableIntegration.checkBlockLight(level, position);
        } else {
            level.getLightEngine().checkBlock(position);
        }
    }

    static void trackExternalLights(Level level, BlockPos owner, Set<BlockPos> lights) {
        if (LOADED) {
            SableIntegration.trackExternalLights(level, owner, lights);
        }
    }
}
