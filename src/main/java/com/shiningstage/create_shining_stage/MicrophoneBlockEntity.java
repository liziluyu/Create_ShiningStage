package com.shiningstage.create_shining_stage;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A placed microphone: the set of speakers it drives, and its hook into the relay registry.
 *
 * <p>It carries no behaviour: the listening range is a global config value (see
 * {@link Config#MICROPHONE_RANGE}), not a per-block one. Anything adjustable here would be adjustable
 * on a placed microphone only, while the same block assembled into a contraption has no block entity
 * to open a value box on — the range would then silently freeze at whatever it held when the structure
 * was assembled.
 */
public class MicrophoneBlockEntity extends SmartBlockEntity {
    private final Set<BlockPos> boundSpeakers = new LinkedHashSet<>();

    public MicrophoneBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.MICROPHONE.get(), pos, blockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // No behaviours: the listening range is a config value; binding state lives in plain fields.
    }

    public Set<BlockPos> getBoundSpeakers() {
        return Collections.unmodifiableSet(boundSpeakers);
    }

    public void addSpeaker(BlockPos pos) {
        boundSpeakers.add(pos);
        notifyUpdate();
    }

    public void removeSpeaker(BlockPos pos) {
        boundSpeakers.remove(pos);
        notifyUpdate();
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
        tag.putLongArray("BoundSpeakers", boundSpeakers.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        boundSpeakers.clear();
        for (long packed : tag.getLongArray("BoundSpeakers")) {
            boundSpeakers.add(BlockPos.of(packed));
        }
    }
}
