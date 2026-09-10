package com.shiningstage.create_shining_stage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SpotlightBlockEntity extends BlockEntity {
    /** Default purple, matching Simulated's MEDIA_OURPLE (RGB 188,118,255). */
    public static final int DEFAULT_COLOR = 0xBC76FF;

    private int laserColor = DEFAULT_COLOR;

    public SpotlightBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.SPOTLIGHT.get(), pos, blockState);
    }

    public int getLaserColor() {
        return laserColor;
    }

    public void setLaserColor(int laserColor) {
        this.laserColor = laserColor;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("LaserColor", laserColor);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("LaserColor")) {
            laserColor = tag.getInt("LaserColor");
        }
    }
}
