package com.shiningstage.create_shining_stage;

import java.util.List;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Shares spotlight rendering settings; illumination remains client-only. */
public class FillLightBlockEntity extends SpotlightBlockEntity {
    public FillLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FILL_LIGHT.get(), pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Range comes from the server-configured common value rather than a value box.
    }

    @Override public int getRange() {
        return level != null && level.isClientSide
            ? StageLightingSettings.clientFillLightRange() : Config.FILL_LIGHT_RANGE.get();
    }

    @Override protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
    }

    @Override protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
    }

    @Override protected Vec3 beamDirection() {
        return FillLightBlock.direction(getBlockState());
    }
}
