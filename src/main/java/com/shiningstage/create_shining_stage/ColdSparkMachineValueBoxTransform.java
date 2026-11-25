package com.shiningstage.create_shining_stage;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Places the spray-height value box on the machine's control face — the face {@code FACING} names, so it
 * is the one a wrench turn carries around the block.
 */
public class ColdSparkMachineValueBoxTransform extends ValueBoxTransform.Sided {
    @Override
    protected Vec3 getSouthLocation() {
        return new Vec3(0.5, 0.5, 15.5 / 16.0);
    }

    /**
     * With the default state (facing=north) the box sits on the north face; a wrench turn carries it to
     * east/south/west together with the control face.
     */
    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        return state.getValue(ColdSparkMachineBlock.FACING) == direction;
    }
}
