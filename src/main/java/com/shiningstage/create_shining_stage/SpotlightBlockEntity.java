package com.shiningstage.create_shining_stage;

import java.util.List;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

public class SpotlightBlockEntity extends SmartBlockEntity {
    /** Default purple, matching Simulated's MEDIA_OURPLE (RGB 188,118,255). */
    public static final int DEFAULT_COLOR = 0xBC76FF;
    /** Adjustable beam length bounds, in blocks. */
    public static final int MIN_RANGE = 2;
    public static final int MAX_RANGE = 32;
    /** Initial beam length on placement. */
    public static final int DEFAULT_RANGE = 16;

    private int laserColor = DEFAULT_COLOR;
    private ScrollValueBehaviour range;

    public SpotlightBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.SPOTLIGHT.get(), pos, blockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        range = new ScrollValueBehaviour(
            Component.translatable("block.create_shining_stage.spotlight.max_length"),
            this, new SpotlightRangeValueBoxTransform())
            .between(MIN_RANGE, MAX_RANGE);
        range.value = DEFAULT_RANGE; // start at a mid value, not max
        behaviours.add(range);
    }

    /** Beam length in blocks (MIN_RANGE..MAX_RANGE), adjusted via the tail value box. */
    public int getRange() {
        return range == null ? DEFAULT_RANGE : range.getValue();
    }

    public int getLaserColor() {
        return laserColor;
    }

    public void setLaserColor(int laserColor) {
        this.laserColor = laserColor;
        notifyUpdate();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("LaserColor", laserColor);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("LaserColor")) {
            laserColor = tag.getInt("LaserColor");
        }
    }
}
