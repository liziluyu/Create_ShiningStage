package com.shiningstage.create_shining_stage;

import java.util.List;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Uses the spotlight's safe ray, owned-light tracking, persistence and sub-level cleanup. */
public class FillLightBlockEntity extends SpotlightBlockEntity {
    public FillLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FILL_LIGHT.get(), pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Range comes from the shared common config rather than a value box.
    }

    @Override public int getRange() {
        return Config.FILL_LIGHT_RANGE.get();
    }

    @Override protected Vec3 beamDirection() {
        return FillLightBlock.direction(getBlockState());
    }
}
