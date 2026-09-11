package com.shiningstage.create_shining_stage;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

public class MicrophoneBlockEntity extends SmartBlockEntity {
    /** Adjustable listening range bounds, in blocks. */
    public static final int MIN_RANGE = 4;
    public static final int MAX_RANGE = 32;
    /** Initial listening range on placement. */
    public static final int DEFAULT_RANGE = 16;

    /** Cached on neighborChanged; read by the sound event handler so the BE never ticks. */
    private boolean redstoneOn;
    private ScrollValueBehaviour range;
    private final Set<BlockPos> boundSpeakers = new LinkedHashSet<>();

    public MicrophoneBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.MICROPHONE.get(), pos, blockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        range = new ScrollValueBehaviour(
            Component.translatable("block.create_shining_stage.microphone.range"),
            this, new MicrophoneRangeValueBoxTransform())
            .between(MIN_RANGE, MAX_RANGE);
        range.value = DEFAULT_RANGE; // start at a mid value, not max
        behaviours.add(range);
    }

    /** Listening range in blocks (MIN_RANGE..MAX_RANGE), adjusted via the top value box. */
    public int getRange() {
        return range == null ? DEFAULT_RANGE : range.getValue();
    }

    public boolean isRedstoneOn() {
        return redstoneOn;
    }

    public void setRedstoneOn(boolean redstoneOn) {
        this.redstoneOn = redstoneOn;
    }

    public Set<BlockPos> getBoundSpeakers() {
        return boundSpeakers;
    }

    public void addSpeaker(BlockPos pos) {
        boundSpeakers.add(pos);
        notifyUpdate();
    }

    public void removeSpeaker(BlockPos pos) {
        boundSpeakers.remove(pos);
        notifyUpdate();
    }

    /** Push a captured sound to every bound speaker. Stale positions (unloaded/broken speakers) skip silently. */
    public void relay(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (BlockPos speakerPos : List.copyOf(boundSpeakers)) {
            if (level.getBlockEntity(speakerPos) instanceof SpeakerBlockEntity speaker) {
                speaker.playRelayed(sound, volume, pitch);
            }
        }
    }

    // Keep the loaded-microphone registry in sync with chunk load/unload.
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            SoundRelayHandler.register(level.dimension(), worldPosition);
        }
    }

    // SmartBlockEntity#setRemoved is final; invalidate() is the Create hook it calls on block
    // removal and chunk unload, so the registry stays in sync with onLoad().
    @Override
    public void invalidate() {
        super.invalidate();
        if (level != null && !level.isClientSide) {
            SoundRelayHandler.unregister(level.dimension(), worldPosition);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("RedstoneOn", redstoneOn);
        tag.putLongArray("BoundSpeakers", boundSpeakers.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        redstoneOn = tag.getBoolean("RedstoneOn");
        boundSpeakers.clear();
        for (long packed : tag.getLongArray("BoundSpeakers")) {
            boundSpeakers.add(BlockPos.of(packed));
        }
    }
}
