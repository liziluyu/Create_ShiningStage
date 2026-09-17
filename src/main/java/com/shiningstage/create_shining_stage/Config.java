package com.shiningstage.create_shining_stage;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * Listening range in blocks, shared by every microphone: a sound is heard while its source stays
     * within this distance of the microphone, and its volume falls off linearly to silence at the edge.
     * 0 mutes every microphone. World-wide and read live, so it applies to microphones already placed
     * and to ones riding a contraption alike — which is the point of moving it off the value box: an
     * assembled structure has no block entity left to open a value box on.
     */
    public static final ModConfigSpec.IntValue MICROPHONE_RANGE = BUILDER
        .comment(
            "Listening range in blocks, shared by every microphone. A sound is heard while its source",
            "stays within this distance, and falls off linearly to silence at the edge. 0 mutes every",
            "microphone.")
        .defineInRange("microphoneRange", 16, 0, 64);

    /**
     * Speakers this close together that heard the same sound are replayed as one source at their common
     * centre, with their volumes summed; see SoundRelayHandler#flush. Speakers further apart stay
     * separate sources, which is what keeps a wide array worth building. 0 replays at every speaker
     * separately, the behaviour before grouping existed.
     */
    public static final ModConfigSpec.DoubleValue SPEAKER_CLUSTER_RADIUS = BUILDER
        .comment(
            "Distance in blocks within which speakers that heard the same sound are replayed as one",
            "source at their common centre, with their volumes summed. 0 replays at every speaker",
            "separately; larger values collapse a wider array into a single source.")
        .defineInRange("speakerClusterRadius", 2.0, 0.0, 64.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
