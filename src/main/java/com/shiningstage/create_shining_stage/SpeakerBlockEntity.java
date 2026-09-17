package com.shiningstage.create_shining_stage;

import java.util.List;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.state.BlockState;

public class SpeakerBlockEntity extends SmartBlockEntity {
    /** Microphone this speaker is bound to, if any. Unbound speakers stay silent decoration. */
    private GlobalPos boundMic;

    public SpeakerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.SPEAKER.get(), pos, blockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // No behaviours: speakers have no adjustable values; binding state lives in plain fields.
    }

    public GlobalPos getBoundMic() {
        return boundMic;
    }

    public void bindTo(GlobalPos boundMic) {
        this.boundMic = boundMic;
        notifyUpdate();
    }

    public void clearBinding() {
        boundMic = null;
        notifyUpdate();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (boundMic != null) {
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, boundMic).result()
                .ifPresent(encoded -> tag.put("BoundMic", encoded));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        boundMic = tag.contains("BoundMic")
            ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("BoundMic")).result().orElse(null)
            : null;
    }
}
