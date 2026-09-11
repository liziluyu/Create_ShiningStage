package com.shiningstage.create_shining_stage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Server-side registry of loaded microphones, plus the relay guard that makes speaker output uncatchable. */
public class SoundRelayHandler {
    /** True while a speaker replays a sound; the event handler skips everything to prevent echo loops. */
    public static boolean relayGuard;

    private static final Map<ResourceKey<Level>, Set<BlockPos>> MICROPHONES = new HashMap<>();

    public static void register(ResourceKey<Level> dimension, BlockPos pos) {
        MICROPHONES.computeIfAbsent(dimension, k -> new HashSet<>()).add(pos);
    }

    public static void unregister(ResourceKey<Level> dimension, BlockPos pos) {
        Set<BlockPos> set = MICROPHONES.get(dimension);
        if (set != null) {
            set.remove(pos);
            if (set.isEmpty()) {
                MICROPHONES.remove(dimension);
            }
        }
    }

    /** Live microphone positions in a dimension, or null when none. */
    public static Set<BlockPos> microphonesIn(ResourceKey<Level> dimension) {
        return MICROPHONES.get(dimension);
    }
}
