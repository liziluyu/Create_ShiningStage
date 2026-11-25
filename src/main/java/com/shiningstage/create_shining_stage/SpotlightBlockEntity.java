package com.shiningstage.create_shining_stage;

import java.util.List;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public class SpotlightBlockEntity extends SmartBlockEntity {
    /** Default warm white (#f8f7d2); dye right-click overrides, amethyst resets to this. */
    public static final int DEFAULT_COLOR = 0xF8F7D2;
    /** Adjustable beam length bounds, in blocks. */
    public static final int MIN_RANGE = 2;
    public static final int MAX_RANGE = 32;
    /** Initial beam length on placement. */
    public static final int DEFAULT_RANGE = 16;

    /** Client light endpoints are refreshed at most every five ticks. */
    public static final int LIGHT_POLL_INTERVAL = 5;
    /** Brightness of the client-only light sources. */
    public static final int LIGHT_LEVEL = 15;

    private int laserColor = DEFAULT_COLOR;
    private ScrollValueBehaviour range;

    public SpotlightBlockEntity(BlockPos pos, BlockState blockState) {
        this(ModBlockEntityTypes.SPOTLIGHT.get(), pos, blockState);
    }

    protected SpotlightBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                  BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        range = new ScrollValueBehaviour(
            Component.translatable("block.create_shining_stage.spotlight.max_length"),
            this, new SpotlightRangeValueBoxTransform()) {
            // Holding a placeable block means "attach it to the tail", not "adjust the range":
            // let the click fall through to block placement instead of opening the value box.
            @Override
            public boolean bypassesInput(ItemStack mainhandItem) {
                return mainhandItem.getItem() instanceof BlockItem;
            }
        }.between(MIN_RANGE, MAX_RANGE);
        range.value = DEFAULT_RANGE; // start at a mid value, not max
        behaviours.add(range);
    }

    /** Beam length in blocks (MIN_RANGE..MAX_RANGE), adjusted via the tail value box. */
    public int getRange() {
        return range == null ? DEFAULT_RANGE : range.getValue();
    }

    protected Vec3 beamDirection() {
        return SpotlightBlock.beamDirection(getBlockState());
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientStageLighting.register(this));
        }
    }

    public int getLaserColor() {
        return laserColor;
    }

    public void setLaserColor(int laserColor) {
        this.laserColor = laserColor;
        notifyUpdate();
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putInt("LaserColor", laserColor);
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        if (tag.contains("LaserColor")) {
            laserColor = tag.getInt("LaserColor");
        }
    }
}
