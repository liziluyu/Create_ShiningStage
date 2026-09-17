package com.shiningstage.create_shining_stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import dev.ryanhcode.sable.Sable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

/**
 * Server-side registry of live microphones and speakers, plus the relay guard that makes speaker
 * output uncatchable.
 *
 * <p>A microphone exists in two shapes. A placed one owns a block entity in the level and is
 * registered by it; one carried by a Create contraption does not — assembling a structure captures
 * its block entities into the contraption's block data and clears them from the level — so a
 * contraption's microphone is re-registered from its contraption instead, and its position is derived
 * from the contraption entity's transform every time it is needed. Both shapes are measured in world
 * space, which is what keeps a mounted microphone's range honest: its captured block position is
 * contraption-local (0,0,0 at the assembly anchor), and using that as a world position would put the
 * microphone near the world origin.
 */
public class SoundRelayHandler {
    /** True while a speaker replays a sound; the event handler skips everything to prevent echo loops. */
    private static boolean relayGuard;

    /** Microphones placed in the level, by dimension. */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> MICROPHONES = new HashMap<>();
    /** Microphones and speakers carried by contraptions, keyed by the world position the block had
     *  when its structure was assembled — the same position a binding to it was stored with. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, MountedMicrophone>> MOUNTED_MICROPHONES =
        new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, MountedSpeaker>> MOUNTED_SPEAKERS = new HashMap<>();

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

    // Contraption mounts are re-registered on every tick rather than once at assembly: an assembly
    // only reaches startMoving on the way in, so a structure re-read from NBT (chunk reload, world
    // reload) would otherwise stay unregistered forever. Nothing here depends on redstone or on
    // Create's contraption controls, which are redstone-driven actor gates: a mounted microphone
    // listens for exactly as long as its structure is assembled.

    /** Keep a contraption-carried microphone in the relay, from the contraption's captured block data. */
    public static void trackMountedMicrophone(MovementContext context) {
        if (context.world.isClientSide) {
            return;
        }
        AbstractContraptionEntity entity = context.contraption.entity;
        if (entity == null) {
            return;
        }
        BlockPos key = mountedKey(context);
        Map<BlockPos, MountedMicrophone> mics =
            MOUNTED_MICROPHONES.computeIfAbsent(context.world.dimension(), k -> new HashMap<>());
        MountedMicrophone known = mics.get(key);
        if (known == null || known.entity != entity) {
            mics.put(key, MountedMicrophone.read(entity, context));
        }
    }

    /** Speaker counterpart of {@link #trackMountedMicrophone}. */
    public static void trackMountedSpeaker(MovementContext context) {
        if (context.world.isClientSide) {
            return;
        }
        AbstractContraptionEntity entity = context.contraption.entity;
        if (entity == null) {
            return;
        }
        BlockPos key = mountedKey(context);
        Map<BlockPos, MountedSpeaker> speakers =
            MOUNTED_SPEAKERS.computeIfAbsent(context.world.dimension(), k -> new HashMap<>());
        MountedSpeaker known = speakers.get(key);
        if (known == null || known.entity != entity) {
            speakers.put(key, new MountedSpeaker(entity, context.localPos));
        }
    }

    /** Drop a mounted device, when its contraption stops (disassembly, discard, unload). */
    public static void forgetMounted(MovementContext context) {
        if (context.world.isClientSide) {
            return;
        }
        BlockPos key = mountedKey(context);
        ResourceKey<Level> dimension = context.world.dimension();
        Map<BlockPos, MountedMicrophone> mics = MOUNTED_MICROPHONES.get(dimension);
        if (mics != null) {
            mics.remove(key);
        }
        Map<BlockPos, MountedSpeaker> speakers = MOUNTED_SPEAKERS.get(dimension);
        if (speakers != null) {
            speakers.remove(key);
        }
    }

    /** The world position a mounted block occupied when its contraption was assembled. Contraption
     *  block positions are relative to that anchor, and stored bindings are world positions, so this
     *  is the one key both sides agree on. */
    private static BlockPos mountedKey(MovementContext context) {
        return context.contraption.anchor.offset(context.localPos);
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
        int range = Config.MICROPHONE_RANGE.get();
        if (range <= 0) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        Set<BlockPos> mics = microphonesIn(dimension);
        Map<BlockPos, MountedMicrophone> mountedMics = MOUNTED_MICROPHONES.get(dimension);
        boolean hasPlaced = mics != null && !mics.isEmpty();
        boolean hasMounted = mountedMics != null && !mountedMics.isEmpty();
        if (!hasPlaced && !hasMounted) {
            return;
        }

        // Every microphone that heard the sound writes into the same map, so a speaker bound to several
        // of them is still one entry; nothing is played until all of them are in.
        Map<BlockPos, Target> heard = new LinkedHashMap<>();
        if (hasPlaced) {
            for (BlockPos micPos : List.copyOf(mics)) {
                if (!(level.getBlockEntity(micPos) instanceof MicrophoneBlockEntity mic)) {
                    continue;
                }
                accumulate(level, heard, sourcePos, range, Vec3.atCenterOf(micPos),
                    mic.getBoundSpeakers(), volume);
            }
        }
        if (hasMounted) {
            Iterator<MountedMicrophone> iterator = mountedMics.values().iterator();
            while (iterator.hasNext()) {
                MountedMicrophone mic = iterator.next();
                if (!mic.isLive()) {
                    // Contraption gone without stopMoving (its chunk unloaded): drop the stale entry.
                    iterator.remove();
                    continue;
                }
                accumulate(level, heard, sourcePos, range, mic.position(), mic.boundSpeakers, volume);
            }
        }
        flush(level, heard, sound.value(), pitch);
    }

    /**
     * Note what one microphone heard, once per speaker bound to it: linear falloff over its listening
     * range, then a volume per speaker. Distances are world space, and ship-safe — a source inside an
     * Aeronautics sub-level sits at far-away coordinates, so Sable maps both points into a common space
     * first.
     *
     * <p>A speaker bound to several microphones is heard through each of them; the closest (loudest) one
     * wins, because the speaker replays the sound once either way.
     */
    private static void accumulate(Level level, Map<BlockPos, Target> heard, Vec3 sourcePos, int range,
                                   Vec3 micPos, Iterable<BlockPos> boundSpeakers, float volume) {
        double d2 = Sable.HELPER.distanceSquaredWithSubLevels(level, sourcePos, micPos);
        if (d2 > (double) range * range) {
            return;
        }
        float newVolume = volume * (1f - (float) Math.sqrt(d2) / range);
        if (newVolume <= 0.01f) {
            return;
        }
        for (BlockPos speakerPos : boundSpeakers) {
            Vec3 speakerAt = speakerPosition(level, speakerPos);
            if (speakerAt == null) {
                continue;
            }
            Target target = heard.get(speakerPos);
            if (target == null) {
                heard.put(speakerPos, new Target(speakerAt, newVolume));
            } else if (newVolume > target.volume) {
                target.position = speakerAt;
                target.volume = newVolume;
            }
        }
    }

    /**
     * Replay everything one sound event was heard by, merging speakers that stand close together — each
     * one joins the nearest group whose centre is within {@link Config#SPEAKER_CLUSTER_RADIUS} of it —
     * into a single source at the group's volume-weighted centre, with the group's volumes summed. Each
     * copy is otherwise a separate sound instance on the client, which rolls its own sample variant,
     * opens its own channel, and subtracts nothing from its neighbours: a row of speakers one block apart
     * is heard as the same noise several times over rather than as one louder sound. The sum is clamped
     * to the 1.0 a single sound can carry; beyond that the copies would only widen the broadcast range,
     * which {@link SoundEvent#getRange(float)} already caps.
     */
    private static void flush(Level level, Map<BlockPos, Target> heard, SoundEvent sound, float pitch) {
        if (heard.isEmpty()) {
            return;
        }
        if (heard.size() == 1) {
            // The common case: one speaker, nothing to group and nothing to allocate for it.
            Target only = heard.values().iterator().next();
            play(level, only.position, sound, only.volume, pitch);
            return;
        }
        double radius = Config.SPEAKER_CLUSTER_RADIUS.get();
        List<Cluster> clusters = new ArrayList<>(heard.size());
        for (Target target : heard.values()) {
            Cluster nearest = null;
            // At radius 0 this still groups speakers standing in the same spot, and nothing else: a
            // cluster is measured from its weighted centre, which only ever coincides with a member
            // while it holds nothing else.
            double nearestD2 = radius * radius;
            for (Cluster cluster : clusters) {
                double d2 = Sable.HELPER.distanceSquaredWithSubLevels(level, cluster.position, target.position);
                if (d2 <= nearestD2) {
                    nearestD2 = d2;
                    nearest = cluster;
                }
            }
            if (nearest == null) {
                clusters.add(new Cluster(target));
            } else {
                nearest.add(target);
            }
        }
        for (Cluster cluster : clusters) {
            play(level, cluster.position, sound, Math.min(1f, cluster.volume), pitch);
        }
    }

    /**
     * Where a bound speaker is right now, or null when it does not play: a block entity standing in the
     * level, or a contraption's captured speaker, which only its contraption entity can place in the
     * world. A binding whose position resolves to neither (broken, or carried off by a structure) goes
     * silent.
     *
     * <p>A placed speaker additionally has to be redstone-powered. A contraption's is exempt: assembling
     * a structure moves its blocks out of the level, so the speaker's world position is surrounded by the
     * contraption entity's own air and no signal can be read there — the contraption controls that could
     * gate it are deliberately left unwired (see {@link SpeakerMovementBehaviour#canBeDisabledVia}). A
     * mounted speaker therefore plays for as long as its structure is assembled, like a mounted
     * microphone listening.
     */
    @Nullable
    private static Vec3 speakerPosition(Level level, BlockPos speakerPos) {
        if (level.getBlockEntity(speakerPos) instanceof SpeakerBlockEntity) {
            return level.getBestNeighborSignal(speakerPos) > 0 ? Vec3.atCenterOf(speakerPos) : null;
        }
        Map<BlockPos, MountedSpeaker> speakers = MOUNTED_SPEAKERS.get(level.dimension());
        MountedSpeaker mounted = speakers == null ? null : speakers.get(speakerPos);
        return mounted != null && mounted.isLive() ? mounted.position() : null;
    }

    /**
     * Play a captured sound. The guard makes the {@link PlayLevelSoundEvent} this call fires invisible
     * to every microphone, so speaker output can never be captured back (no echo loops, locally or
     * across a network of speakers). Server-thread only: save/restore keeps the guard correct even if
     * a replay ever nests.
     */
    private static void play(Level level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        boolean previous = relayGuard;
        relayGuard = true;
        try {
            level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS, volume, pitch);
        } finally {
            relayGuard = previous;
        }
    }

    /** A microphone or speaker riding a contraption: the contraption entity says where its block is now. */
    private abstract static class MountedDevice {
        final AbstractContraptionEntity entity;
        final BlockPos localPos;

        MountedDevice(AbstractContraptionEntity entity, BlockPos localPos) {
            this.entity = entity;
            this.localPos = localPos;
        }

        /** Current world position of the block's centre, through the contraption's rotation and anchor. */
        Vec3 position() {
            return entity.toGlobalVector(Vec3.atLowerCornerOf(localPos).add(0.5, 0.5, 0.5), 1);
        }

        /** False once the contraption entity is gone: discarded, or its chunk unloaded. */
        boolean isLive() {
            return entity.isAlive();
        }
    }

    /**
     * A contraption's microphone, read from the block data captured at assembly. The live block entity
     * is gone, so its bound speakers come from that NBT; a binding cannot change while the structure is
     * assembled (it needs a block entity to rebind), so reading it once here is enough. The listening
     * range is not read at all — it is a config value, the same for this microphone as for a placed one.
     */
    private static final class MountedMicrophone extends MountedDevice {
        final List<BlockPos> boundSpeakers;

        MountedMicrophone(AbstractContraptionEntity entity, BlockPos localPos, List<BlockPos> boundSpeakers) {
            super(entity, localPos);
            this.boundSpeakers = boundSpeakers;
        }

        static MountedMicrophone read(AbstractContraptionEntity entity, MovementContext context) {
            CompoundTag data = context.blockEntityData;
            List<BlockPos> speakers = new ArrayList<>();
            if (data != null) {
                for (long packed : data.getLongArray("BoundSpeakers")) {
                    speakers.add(BlockPos.of(packed));
                }
            }
            return new MountedMicrophone(entity, context.localPos, List.copyOf(speakers));
        }
    }

    /** A contraption's speaker, which is nothing but a moving playback position. */
    private static final class MountedSpeaker extends MountedDevice {
        MountedSpeaker(AbstractContraptionEntity entity, BlockPos localPos) {
            super(entity, localPos);
        }
    }

    /** One speaker's part in a sound event: where it stands now, and how loud a microphone heard it. */
    private static final class Target {
        Vec3 position;
        float volume;

        Target(Vec3 position, float volume) {
            this.position = position;
            this.volume = volume;
        }
    }

    /** Speakers replayed as one source: their volume-weighted centre, and the sum of their volumes. */
    private static final class Cluster {
        Vec3 position;
        float volume;

        Cluster(Target first) {
            position = first.position;
            volume = first.volume;
        }

        void add(Target target) {
            float total = volume + target.volume;
            // Weighted mean, so the loudest speaker in the group pulls the source toward itself.
            position = new Vec3(
                (position.x * volume + target.position.x * target.volume) / total,
                (position.y * volume + target.position.y * target.volume) / total,
                (position.z * volume + target.position.z * target.volume) / total);
            volume = total;
        }
    }
}
