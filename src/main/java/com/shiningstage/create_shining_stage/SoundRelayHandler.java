package com.shiningstage.create_shining_stage;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import dev.ryanhcode.sable.Sable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

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
        Set<BlockPos> set = MICROPHONES.get(dimension);
        return set == null ? null : Collections.unmodifiableSet(set);
    }

    public static void onAtPosition(PlayLevelSoundEvent.AtPosition event) {
        handle(event.getLevel(), event.getPosition(), event.getSound(),
            event.getOriginalVolume(), event.getOriginalPitch());
    }

    public static void onAtEntity(PlayLevelSoundEvent.AtEntity event) {
        handle(event.getLevel(), event.getEntity().position(), event.getSound(),
            event.getOriginalVolume(), event.getOriginalPitch());
    }

    private static void handle(Level level, Vec3 sourcePos, @Nullable Holder<SoundEvent> sound,
                               float volume, float pitch) {
        if (relayGuard || level.isClientSide || sound == null) {
            return;
        }
        Set<BlockPos> mics = microphonesIn(level.dimension());
        if (mics == null || mics.isEmpty()) {
            return;
        }
        for (BlockPos micPos : List.copyOf(mics)) {
            if (!(level.getBlockEntity(micPos) instanceof MicrophoneBlockEntity mic) || mic.getRange() <= 0) {
                continue;
            }
            int range = mic.getRange();
            // Ship-safe distance: a source in an Aeronautics sub-level sits at far-away coordinates,
            // so a vanilla distance check would explode. Sable maps both points into a common space.
            double d2 = Sable.HELPER.distanceSquaredWithSubLevels(level, sourcePos, Vec3.atCenterOf(micPos));
            if (d2 > (double) range * range) {
                continue;
            }
            float newVolume = volume * (1f - (float) Math.sqrt(d2) / range);
            if (newVolume <= 0.01f) {
                continue;
            }
            mic.relay(sound.value(), newVolume, pitch);
        }
    }
}
